package com.example.weconnect.utils;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProvinceWardLoader {

    public static class Ward {
        public String ward_name;
        public String search_text;
    }

    public static class Province {
        public String province_name;
        public String province_search;
        public List<Ward> wards;
    }

    private static List<Province> cache = null;

    public static List<Province> load(Context context) {
        if (cache != null) return cache;
        try {
            InputStream is = context.getAssets().open("provinces_wards.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            Type type = new TypeToken<List<Province>>() {}.getType();
            cache = new Gson().fromJson(json, type);
            return cache;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    // Trả về danh sách tên tỉnh/thành cho dropdown
    public static List<String> getProvinceNames(List<Province> provinces) {
        List<String> names = new ArrayList<>();
        for (Province p : provinces) names.add(p.province_name);
        return names;
    }

    // Tìm province theo tên chính xác
    public static Province findByName(List<Province> provinces, String name) {
        if (name == null) return null;
        for (Province p : provinces) {
            if (p.province_name.equals(name)) return p;
        }
        return null;
    }
}
