package com.yuyan.imemodule.libs.pinyin4j;

import com.yuyan.imemodule.libs.pinyin4j.multipinyin.Trie;

import java.io.IOException;

class ChineseToPinyinResource {
    
    private Trie unicodeToHanyuPinyinTable = null;

    
    private void setUnicodeToHanyuPinyinTable(Trie unicodeToHanyuPinyinTable) {
        this.unicodeToHanyuPinyinTable = unicodeToHanyuPinyinTable;
    }

    
    Trie getUnicodeToHanyuPinyinTable() {
        return unicodeToHanyuPinyinTable;
    }

    
    private ChineseToPinyinResource() {
        initializeResource();
    }

    
    private void initializeResource() {
        try {
            final String resourceName = "pinyindb/unicode_to_hanyu_pinyin.txt";
            final String resourceMultiName = "pinyindb/multi_pinyin.txt";
            setUnicodeToHanyuPinyinTable(new Trie());
            getUnicodeToHanyuPinyinTable().load(ResourceHelper.getResourceInputStream(resourceName));
            getUnicodeToHanyuPinyinTable().loadMultiPinyin(ResourceHelper.getResourceInputStream(resourceMultiName));
        } catch (IOException ignored) {
        }
    }

    String[] parsePinyinString(String pinyinRecord) {
        if (null != pinyinRecord) {
            int indexOfLeftBracket = pinyinRecord.indexOf(Field.LEFT_BRACKET);
            int indexOfRightBracket = pinyinRecord.lastIndexOf(Field.RIGHT_BRACKET);
            String stripedString = pinyinRecord.substring(indexOfLeftBracket + Field.LEFT_BRACKET.length(), indexOfRightBracket);
            return stripedString.split(Field.COMMA);
        } else return null;
    }

    
    static ChineseToPinyinResource getInstance() {
        return ChineseToPinyinResourceHolder.theInstance;
    }

    
    private static class ChineseToPinyinResourceHolder {
        static final ChineseToPinyinResource theInstance = new ChineseToPinyinResource();
    }

    
    class Field {
        static final String LEFT_BRACKET = "[";

        static final String RIGHT_BRACKET = "]";

        static final String COMMA = ",";
    }
}
