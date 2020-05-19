package com.learn.csvdownload.util;


import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: suyouliang
 * Date: 4/2/19
 * Time: 8:43 PM
 * Description:
 */
public class DateUtil {
    /**
     * 时间转换格式yyyy年MM月dd日 HH:mm:ss
     *
     * @param date
     * @return
     */
    public static String getYMDMms(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
        return formatter.format(date);
    }

}