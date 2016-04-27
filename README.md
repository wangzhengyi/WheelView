# WheelView

## Demo

![WheelView](https://github.com/wangzhengyi/WheelView/raw/master/screenshots/device-2016-04-25-164354.png)

--------
## How to use

### layout

```xml
<com.wzy.wheelview.library.WheelView
    android:id="@+id/id_wheel_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

自定义属性包括:

* center_text_color:中间文字的颜色.
* center_text_size:中间文字的大小.
* side_text_color:两边文字的颜色.
* side_text_size:两边文字的大小.
* center_margin_top:中间文字距离上方文字的距离.
* center_margin_bottom:中间文字距离下方文字的距离.
* paint_align:中间文字的paintAlign,默认是center.
* show_bottom_text:是否绘制下方文字.

### Activity
```java
private void initView() {
    WheelView wheelView = (WheelView) findViewById(R.id.id_wheel_view);
    List<String> content = new ArrayList<>();
    for (int i = 0; i < 24; i ++) {
        content.add(String.format(Locale.getDefault(), "%02d", i));
    }
    wheelView.setItemListAndScaleContent(content, "小时");
}
```

-------
# 原理讲解

## 手势检测功能

首先给出手势检测的流程图如下:

![onTouchEvent](https://github.com/wangzhengyi/WheelView/raw/master/screenshots/GestureDetector.png)


# License
>Copyright 2014 Wang Zhengyi
>
>Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
> 
>    http://www.apache.org/licenses/LICENSE-2.0
> 
> Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.