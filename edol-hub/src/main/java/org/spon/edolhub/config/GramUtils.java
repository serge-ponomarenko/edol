package org.spon.edolhub.config;

public final class GramUtils {

    public static final double GRAM_EPSILON = 0.001;

    public static double round(double grams) {
        return Math.round(grams * 10.0) / 10.0;
    }

}