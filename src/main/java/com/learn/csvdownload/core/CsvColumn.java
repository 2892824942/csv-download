package com.learn.csvdownload.core;

import java.lang.annotation.*;


@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CsvColumn {
    /**
     * 该列数据的标题名
     *
     * @return String
     */
    String title();

    /**
     * 排序规则，按照asc排序。如果不初始化该字段，将按照field定义的先后顺序，
     * 对field定义的先后顺序强依赖是不健壮的，如果对顺序苛求的场景，应初始化该字段。
     *
     * @return int
     */
    int weight() default 0;

    /**
     * 通过的SpringEL表达，处理自定义显示值的需求，
     *
     * @return String
     */
    String springEL() default "";

    /**
     * 分组:同一个VO不同需求场景，在CSV文件中需要展示的字段可能存在不同，通过此字段区分
     * 定义该字段后，想要对应方法生成的CSV中包含被注解的字段，必须在调用CSVUtil方法时加入该参数。
     * 只有显式声明group的方法才【会】加入被注解字段
     *
     * @return String
     */
    String doGroup() default "";

    /**
     * 分组:同一个VO不同需求场景，在CSV文件中需要展示的字段可能存在不同，通过此字段区分
     * 定义该字段后，想要对应方法生成的CSV中剔除被注解的字段
     * 只有显式声明group的方法才【不会】加入被注解字段
     *
     * @return String
     */
    String unDoGroup() default "";
}