package com.yuyan.imemodule.libs.pinyin4j.format;

public class HanyuPinyinCaseType {

  
  public static final HanyuPinyinCaseType UPPERCASE = new HanyuPinyinCaseType("UPPERCASE");

  
  public static final HanyuPinyinCaseType LOWERCASE = new HanyuPinyinCaseType("LOWERCASE");

  
  public String getName() {
    return name;
  }

  
  protected void setName(String name) {
    this.name = name;
  }

  
  protected HanyuPinyinCaseType(String name) {
    setName(name);
  }

  protected String name;
}
