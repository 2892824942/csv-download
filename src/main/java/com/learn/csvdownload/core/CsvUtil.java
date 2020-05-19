package com.learn.csvdownload.core;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User: suyouliang
 * Date: 2019/1/4
 * Time: 4:39 PM
 * Description: 使用SpringEL+自定义注解CsvColumn实现的Csv内容初始化通用工具
 * 功能点：
 * 1.当数据为空时，默认输出头信息
 * 2.处理因为单元格内容包含特殊字符导致的CSV显示格式异常的问题
 * 3.基于缓存提高CSV文件格式及头信息获取性能
 * 4.基于SpringEl实现自定义输出格式
 * 5.基于ResponseEntity屏蔽底层Servlet Api
 * 6.
 * <p>
 * 使用：
 * 在需要导出的DTO或PO的field加上@CsvColumn，该field就会写入csv
 * 本类为了方便，引入了lang3包的StringUtils和Guava的Cache 如果项目本身没有相关依赖或者对于外部依赖有严格的控制
 * 请自行重构。其中Cache是基于concurrentHashMap实现（并发安全）
 * 重构时需注意缓存初始化时的并发安全问题,可使用ConcurrentHashMap也可以使用锁...
 */
@Slf4j
public class CsvUtil {
    /**
     * CSV文件列分隔符
     */
    private static final String CSV_COLUMN_SEPARATOR = ",";

    /**
     * CSV文件数据出现和分隔符相同时的替换字符（也可以转译）
     */
    private static final String CSV_COLUMN_SEPARATOR_REPLACE = ".";

    /**
     * CSV文件换行符
     */
    private static final String CSV_RN = "\r\n";
    /**
     * CSV文件名前缀（按照需求自行重构）
     */
    private static final String FILE_PREFIX = "prefix_";


    /**
     * 被@CsvColumn注解的字段缓存（如果存在大量（上百个）的csv下载，可以考虑缓存增加失效时间）
     * key：Class+group
     * value：Map<String, CsvColumn>
     * key：filedName  value：CsvColumn
     */
    private static final Cache<String, Map<String, CsvColumn>> annotationMapCache = CacheBuilder.newBuilder().build();
    /**
     * csv文件标题行数据缓存
     * key：Map<String, CsvColumn>对象的hashCode Hex
     * value：Csv行数据
     */
    private static final Cache<String, String> csvHeadLineCache = CacheBuilder.newBuilder().build();

    public static <T> ResponseEntity<Resource> sendDataStream(List<T> dataList, String fileName, Class<T> dataClass) {
        return sendDataStream(dataList, fileName, null, dataClass);
    }

    public static <T> ResponseEntity<Resource> sendDataStream(List<T> dataList, String fileName, String group, Class<T> dataClass) {
        final Map<String, CsvColumn> filedAnnotationMap = getFiledAnnotationMap(dataClass, group);
        StringBuilder builder = new StringBuilder();
        //1..拼接头信息
        builder.append(getCsvHeaderLine(filedAnnotationMap));
        builder.append(CSV_RN);
        //2.拼接数据列
        if (!CollectionUtils.isEmpty(dataList)) {
            dataList.forEach(obj -> builder.append(getCsvOneLine(filedAnnotationMap, obj, dataClass)));
        }
        //3.将数据写入response流中
        return writeData(builder.toString(), fileName);
    }

    /**
     * 先从缓存中获取，如果获取不到，初始化数据并放入本地缓存
     * 缓存过程线程安全
     *
     * @param dataClass 数据对应class
     * @param group     分组编码
     * @return getIfPresent排序后的Map
     */
    private static Map<String, CsvColumn> getFiledAnnotationMap(Class<?> dataClass, String group) {
        return Optional.ofNullable(annotationMapCache.getIfPresent(getAnnotationMapKey(dataClass, group)))
                .orElseGet(() -> initFiledAnnotationMap(dataClass, group));
    }

    private static Map<String, CsvColumn> initFiledAnnotationMap(Class<?> dataClass, String group) {
        Map<String, CsvColumn> columnMap = new LinkedHashMap<>();
        //1.查找带有@CsvColumn注解的field，并装入CsvColumnMap
        Arrays.asList(dataClass.getDeclaredFields()).forEach(field -> {
            CsvColumn annotation = field.getDeclaredAnnotation(CsvColumn.class);
            if (annotation != null) {
                field.setAccessible(true);
                //剔除分组过滤的字段
                if ((StringUtils.isNotEmpty(group) && StringUtils.isNotEmpty(annotation.doGroup()) && !annotation.doGroup().equals(group)) ||
                        (StringUtils.isNotEmpty(annotation.unDoGroup()) && annotation.unDoGroup().equals(group))) {
                    return;
                }
                columnMap.put(field.getName(), annotation);
            }
        });
        //2.根据FileCsvColumn的weight属性对CsvColumnMap进行排序.
        final Map<String, CsvColumn> filedAnnotationMap = sortByValue(columnMap);
        //3.加入缓存
        annotationMapCache.put(getAnnotationMapKey(dataClass, group), filedAnnotationMap);
        return filedAnnotationMap;
    }

    /**
     * 拼接CVS表格一行数据
     *
     * @param filedAnnotationMap
     * @return
     */
    private static <T> String getCsvOneLine(Map<String, CsvColumn> filedAnnotationMap, T lineDate, Class<?> dataClass) {
        StringBuilder lineStrBuilder = new StringBuilder();
        //1循环data，一个obj代表一行
        filedAnnotationMap.forEach((key, value) -> {
            //2循环filedAnnotationMap，一个Entity代表一列的数据
            try {
                Field field = dataClass.getDeclaredField(key);
                field.setAccessible(true);
                String dataColumn = Optional.ofNullable(field.get(lineDate)).orElse("").toString();
                //3解析SpringEL表达式，处理自定义的输出格式需求
                if (StringUtils.isNotEmpty(value.springEL())) {
                    dataColumn = getSpringELValue(value.springEL(), lineDate);
                }
                //4转译处理(放在el解析后，防止el解析逻辑出现幺蛾子（EL转译后出现CSV逻辑符号）)
                lineStrBuilder.append(symbolTranslation(dataColumn));
                lineStrBuilder.append(CSV_COLUMN_SEPARATOR);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                log.error("CsvUtil根据反射操作属性异常，异常信息{0}", e);
            }
        });
        return lineStrBuilder.append(CSV_RN).toString();
    }

    /**
     * 获取
     *
     * @param filedAnnotationMap
     * @return String
     */
    private static String getCsvHeaderLine(Map<String, CsvColumn> filedAnnotationMap) {
        return StringUtils.join(Optional.ofNullable(csvHeadLineCache.getIfPresent(Integer.toHexString(filedAnnotationMap.hashCode())))
                .orElseGet(() -> initCsvHeaderLine(filedAnnotationMap)), CSV_COLUMN_SEPARATOR);
    }

    /**
     * 获取annotationMapCache的key
     *
     * @param dataClass
     * @param group
     * @return
     */
    private static String getAnnotationMapKey(Class<?> dataClass, String group) {
        return dataClass.getName().concat(Optional.ofNullable(group).orElse(""));
    }

    private static String initCsvHeaderLine(Map<String, CsvColumn> filedAnnotationMap) {
        final String csvHeaderLineStr = StringUtils.join(filedAnnotationMap.values().stream()
                .map(CsvColumn::title).collect(Collectors.toList()), CSV_COLUMN_SEPARATOR);
        csvHeadLineCache.put(Integer.toHexString(filedAnnotationMap.hashCode()), csvHeaderLineStr);
        return csvHeaderLineStr;
    }

    /**
     * 根据csv的内容 使用HttpServletResponse 发送
     *
     * @param data csv内容
     * @return
     */
    private static ResponseEntity<Resource> writeData(String data, String fileName) {
        //此行为了标示文件解析的格式，不加在Excel上会乱码，wps好像没事
        data = "\ufeff".concat(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=".concat(getRealCsvFileName(fileName) + ".csv"))
                .body(new InputStreamResource(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));

    }

    private static String getSpringELValue(String springEL, Object sourceObj) {
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(springEL);
        return exp.getValue(sourceObj) + "";
    }

    /**
     * 根据FileColumn中的weight属性为Map<String, CsvColumn> map排序
     *
     * @param map 需要排序的map
     * @return 排序后的CsvColumn Map
     */
    private static Map<String, CsvColumn> sortByValue(Map<String, CsvColumn> map) {
        Map<String, CsvColumn> result = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().weight()))
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static String getRealCsvFileName(String fileName) {
        fileName = StringUtils.isEmpty(fileName) ? "default" : fileName.trim();
        return CsvUtil.FILE_PREFIX
                .concat(fileName.replaceAll(" ", "-").concat("_"))
                .concat(System.currentTimeMillis() + "");
    }

    /**
     * 特殊符号转译，防止内容中包含CSV的逻辑符号
     * 针对英文的",",与CSV的列标示冲突，统一更换为中文的"，"
     * 针对"\r\n",与CSV的行标示冲突，统一更换为""
     * 也可以自己定制
     *
     * @param dataColumn 单元格内容
     * @return 转译后的单元格内容
     */
    private static String symbolTranslation(String dataColumn) {
        return dataColumn.replaceAll(CSV_RN, "").replaceAll(CSV_COLUMN_SEPARATOR, "，");
    }

}