package com.akillisira.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Bluetooth cihaz adından arabaya bağlanıldığını tahmin eder. */
public final class Car {
    private Car() {}

    private static final Set<String> WORDS = new HashSet<>(Arrays.asList(
        "car", "araba", "auto", "carkit", "sync", "mzd", "uconnect", "headunit", "multimedia", "carplay",
        "toyota", "renault", "fiat", "ford", "hyundai", "peugeot", "bmw", "mercedes", "mb", "audi", "vw",
        "volkswagen", "opel", "skoda", "kia", "honda", "nissan", "seat", "dacia", "citroen", "tesla",
        "volvo", "mazda", "togg", "chevrolet", "mitsubishi", "suzuki", "jeep", "lexus", "subaru", "cupra",
        "alfa", "porsche", "chery", "byd", "mg", "pioneer", "kenwood", "jvc", "alpine", "blaupunkt",
        "clio", "megane", "egea", "corolla", "golf", "passat", "polo", "focus", "astra", "octavia", "i20",
        "tucson", "duster", "sandero", "qashqai", "civic", "c-hr", "chr", "t10x"));

    public static boolean looksLikeCar(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("my car") || n.contains("car kit") || n.contains("car audio") || n.contains("android auto")) return true;
        for (String w : n.split("[^a-z0-9-]+")) {
            if (WORDS.contains(w)) return true;
        }
        return false;
    }
}
