package com.whiteblink.utils;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/*
 * Range Objects are used to divide total records of the table into parts.
 * Then all the parts are processed by different threads to make the sync faster. (Pagination for SQL Result)
 * */
@Data
public class Range {
    private int from;
    private int to;

    /*
     * Divides The Number Of Records Into ranges
     * */
    public static List<Range> getRanges(int count, int dbThreads) {
        ArrayList<Range> arrayList = new ArrayList<>();
        int parts = count / dbThreads;
        float partFloat = ((float) count / (float) dbThreads) - (float) parts;
        int temp = Math.round(partFloat * dbThreads);
        int t = 0;
        for (int i = 2; i <= dbThreads + 1; i++) {
            Range range = new Range();
            range.from = t + 1;
            t = (i - 1) * parts;
            range.to = t;
            range.to = (i == dbThreads + 1) ? t + temp : t;
            arrayList.add(range);
        }
        return arrayList;
    }

}