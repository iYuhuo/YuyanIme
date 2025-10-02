package com.yuyan.imemodule.libs.pinyin4j;

import com.yuyan.imemodule.application.Launcher;
import java.io.BufferedInputStream;
import java.io.IOException;

class ResourceHelper {
  
  static BufferedInputStream getResourceInputStream(String resourceName) throws IOException{
      return new BufferedInputStream(Launcher.Companion.getInstance().getContext().getAssets().open(resourceName));
  }
}
