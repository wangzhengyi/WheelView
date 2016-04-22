package com.wzy.wheelview;

import android.app.Activity;
import android.os.Bundle;

import com.wzy.wheelview.library.WheelView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        WheelView wheelView = (WheelView) findViewById(R.id.id_wheel_view);
        List<String> content = new ArrayList<>();
        for (int i = 0; i < 24; i ++) {
            content.add(String.format(Locale.getDefault(), "%02d", i));
        }
        wheelView.setItemListAndScaleContent(content, "小时");
    }
}
