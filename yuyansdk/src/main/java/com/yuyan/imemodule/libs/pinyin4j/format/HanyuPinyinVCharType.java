package com.yuyan.imemodule.libs.pinyin4j.format;

public class HanyuPinyinVCharType {

  
  public static final HanyuPinyinVCharType WITH_U_AND_COLON =
      new HanyuPinyinVCharType("WITH_U_AND_COLON");

  
  public static final HanyuPinyinVCharType WITH_V = new HanyuPinyinVCharType("WITH_V");

  
  public static final HanyuPinyinVCharType WITH_U_UNICODE =
      new HanyuPinyinVCharType("WITH_U_UNICODE");

  
  public String getName() {
    return name;
  }

  
  protected void setName(String name) {
    this.name = name;
  }

  
  protected HanyuPinyinVCharType(String name) {
    setName(name);
  }

  protected String name;
}
