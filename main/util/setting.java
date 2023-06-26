package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

/**
 * @作者 Brown
 * @日期 2023/6/20 13:17
 */
public class setting {
    private static final HashMap<String, String> conf;


    static {
        Properties Prop = new Properties();
        try {
            Prop.load(new FileInputStream("resources/settings.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        conf = new HashMap<>();
        conf.put("question.markdown_format", Prop.getProperty("question.markdown_format"));
        conf.put("question.markdown_path", updatePath(Prop.getProperty("question.markdown_path")));
        conf.put("question.xlsx_path", Prop.getProperty("question.xlsx_path"));
        conf.put("question.onlyTemplates", Prop.getProperty("question.onlyTemplates"));
        conf.put("request.session", Prop.getProperty("request.session"));
        conf.put("request.url", Prop.getProperty("request.url"));
        conf.put("request.graphql_url", Prop.getProperty("request.graphql_url"));
    }

    private static String updatePath(String path) {
        if (path != null && path.length() > 0 && path.charAt(path.length() - 1) != '/')
            path += '/';
        return path;
    }

    public String getConf(String props) {
        String s = conf.get(props);
        return s == null ? "" : s;
    }
}
