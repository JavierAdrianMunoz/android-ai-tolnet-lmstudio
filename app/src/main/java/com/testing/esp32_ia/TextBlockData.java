package com.testing.esp32_ia;

import java.util.LinkedHashMap;
import java.util.Map;

enum BlockType {
    QUESTION,
    OPTIONS,
    PARAGRAPH,
    UNKNOWN
}

class TextBlockData {
    BlockType type;
    String content;
    long timestamp;

    String question;
    String paragraph;

    Map<String, String> options; // clave: A, B, C...

    public TextBlockData(BlockType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
    public TextBlockData() {
        this.timestamp = System.currentTimeMillis();
        this.options = new LinkedHashMap<>();
    }

}
