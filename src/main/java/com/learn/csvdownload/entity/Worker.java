package com.learn.csvdownload.entity;

import com.learn.csvdownload.core.CsvColumn;
import lombok.Data;

import java.util.Date;

@Data
public class Worker {
    @CsvColumn(title = "姓名")
    private String name;
    @CsvColumn(title = "年龄", weight = 2)
    private Integer age;
    @CsvColumn(title = "性别", weight = 4, springEL = "sex==0?'女':'男'")
    private Integer sex;
    //这里的时间util更换成自己的
    @CsvColumn(title = "生日", weight = 3, springEL = "T(com.learn.csvdownload.util.DateUtil).getYMDMms(birthDay)")
    private Date birthDay;
    @CsvColumn(title = "身份证号", weight = 3, unDoGroup = "myGroup")
    private String IdCard;

}