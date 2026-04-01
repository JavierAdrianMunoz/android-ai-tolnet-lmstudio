package com.testing.esp32_ia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StructuredBlock {
    public String raw;
    public  String cleaned;
    BlockType type;
    String rawText;

    public String question;
    List<String> options = new ArrayList<>();
    String paragraph;
    public Map<String, String> optionsMap = new LinkedHashMap<>();
    long timestamp;

    public StructuredBlock(String rawText) {
        this.rawText = rawText;
        this.timestamp = System.currentTimeMillis();
    }

}