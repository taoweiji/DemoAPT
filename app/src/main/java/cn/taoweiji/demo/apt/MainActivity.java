package cn.taoweiji.demo.apt;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import cn.taoweiji.demo.annotation.DIActivity;
import cn.taoweiji.demo.annotation.DIView;
import cn.taoweiji.demo.annotation.Test;

@Test
@DIActivity
public class MainActivity extends Activity {
    @DIView(R.id.text)
    TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DIMainActivity.bindView(this);
        textView.setText("Hello World!");
    }
}