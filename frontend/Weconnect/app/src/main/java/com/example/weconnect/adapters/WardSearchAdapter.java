package com.example.weconnect.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.example.weconnect.utils.ProvinceWardLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hỗ trợ tìm kiếm phường/xã có dấu hoặc không dấu, partial match.
 * Ví dụ: "phu", "Phú", "luong", "Lương" đều ra "Phường Phú Lương".
 */
public class WardSearchAdapter extends ArrayAdapter<String> {

    private final List<String> allWardNames;
    private final List<String> allSearchTexts;
    private List<String> filteredNames;
    private final WardFilter wardFilter = new WardFilter();

    public WardSearchAdapter(Context context, List<ProvinceWardLoader.Ward> wards) {
        super(context, android.R.layout.simple_dropdown_item_1line);
        allWardNames = new ArrayList<>();
        allSearchTexts = new ArrayList<>();
        for (ProvinceWardLoader.Ward w : wards) {
            allWardNames.add(w.ward_name);
            allSearchTexts.add(w.search_text);
        }
        filteredNames = new ArrayList<>(allWardNames);
    }

    @Override
    public Filter getFilter() {
        return wardFilter;
    }

    @Override
    public int getCount() {
        return filteredNames.size();
    }

    @Override
    public String getItem(int position) {
        return (position >= 0 && position < filteredNames.size()) ? filteredNames.get(position) : null;
    }

    // Xóa dấu tiếng Việt về ASCII để so sánh
    private static String removeDiacritics(String s) {
        if (s == null) return "";
        final String[] FROM = {
            "àáâãäåāắặấầậẩẫảạăằ","ÀÁÂÃÄÅẮẶẤẦẬẨẪẢẠĂ Ằ",
            "èéêếềệểễẹẻ","ÈÉÊẾỀỆỂỄẸẺ",
            "ìíịỉĩ","ÌÍỊỈĨ",
            "òóôõöøơốồộổỗởờợọỏ","ÒÓÔÕÖØƠỐỒỘỔỖỞỜỢỌỎ",
            "ùúưứừựửữụủũ","ÙÚƯỨỪỰỬỮỤỦŨ",
            "ỳýỵỷỹ","ỲÝỴỶỸ",
            "đ","Đ"
        };
        final String[] TO = {
            "aaaaaaaaaaaaaaaaaaa","aaaaaaaaaaaaaaaaaaa",
            "eeeeeeeeee","eeeeeeeeee",
            "iiiii","iiiii",
            "ooooooooooooooooo","ooooooooooooooooo",
            "uuuuuuuuuuu","uuuuuuuuuuu",
            "yyyyy","yyyyy",
            "d","d"
        };
        StringBuilder result = new StringBuilder(s.length());
        outer:
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            for (int g = 0; g < FROM.length; g++) {
                int idx = FROM[g].indexOf(c);
                if (idx >= 0) {
                    result.append(TO[g].charAt(idx < TO[g].length() ? idx : 0));
                    continue outer;
                }
            }
            result.append(c);
        }
        return result.toString().toLowerCase();
    }

    private class WardFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<String> matches;

            if (constraint == null || constraint.length() == 0) {
                matches = new ArrayList<>(allWardNames);
            } else {
                String query = removeDiacritics(constraint.toString().trim());
                matches = new ArrayList<>();
                for (int i = 0; i < allSearchTexts.size(); i++) {
                    if (allSearchTexts.get(i).contains(query)) {
                        matches.add(allWardNames.get(i));
                    }
                }
            }

            results.values = matches;
            results.count = matches.size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredNames = (List<String>) results.values;
            if (results.count > 0) notifyDataSetChanged();
            else notifyDataSetInvalidated();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return (String) resultValue;
        }
    }
}
