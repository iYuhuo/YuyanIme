package com.yuyan.imemodule.libs.pinyin4j.format;

public class HanyuPinyinToneType {

  
  public static final HanyuPinyinToneType WITH_TONE_NUMBER =
      new HanyuPinyinToneType("WITH_TONE_NUMBER");

  
  public static final HanyuPinyinToneType WITHOUT_TONE = new HanyuPinyinToneType("WITHOUT_TONE");

  
  public static final HanyuPinyinToneType WITH_TONE_MARK =
      new HanyuPinyinToneType("WITH_TONE_MARK");

  
  public String getName() {
    return name;
  }

  
  protected void setName(String name) {
    this.name = name;
  }

  protected HanyuPinyinToneType(String name) {
    setName(name);
  }

  protected String name;
}
