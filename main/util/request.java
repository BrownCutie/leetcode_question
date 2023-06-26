package util;


import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * @作者 Brown
 * @日期 2023/6/19 21:21
 */
public class request {

    public static setting setting = new setting();
    private Request.Builder Builder = null;
    private OkHttpClient client = null;

    public request(String url) {
        String session = setting.getConf("request.session");
        if (session == null || session.equals("")) {
            System.out.println("没有配置您的session 获取的数据可能和你的实际情况不符合");
        }
        String graphqlUrl = setting.getConf("request.graphql_url");
        if (graphqlUrl == null || graphqlUrl.equals("")) {
            System.out.println("请配置graphqlUrl");
            return;
        }

        client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        Builder = new Request.Builder()
                .addHeader("Content-Type", "application/graphql")
                .addHeader("Referer", url)
                .addHeader("Cookie", "LEETCODE_SESSION=" + session)
                .url(graphqlUrl);

    }

    public JSONObject start(String body) throws IOException, JSONException {

        Request request = Builder
                .post(RequestBody.create(MediaType.parse("application/graphql; charset=utf-8"), body))//如果用application/graphql 则只能穿query不能传variables
                .build();
        String questionStr = client.newCall(request).execute().body().string();
        return new JSONObject(questionStr);
    }
}
