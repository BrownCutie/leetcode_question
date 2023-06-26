import org.json.JSONException;

import java.io.IOException;

/**
 * @作者 Brown
 * @日期 2023/6/26 22:24
 */
public class Main {
    public static void main(String[] args) throws IOException, JSONException, InterruptedException {
        //获取所有题目相关信息 存储到xlsx中并生成对应markdown文件 但是只生成已经通过的题目
        new allQuestion().getAllQuestion(true, true, true);

        //单独一道题获取示例
        question q = new question("two-sum");
        q.getData();//去leetcode上获取相关信息
        if (q.status.equals("已解答")) q.getSubmit();//已解决会去获取最新一条java语言的AC代码 可以去这个方法找修改自己保存的语言
        q.writeToMarkdown();//将信息写到markdown上 如果上述获取代码会默认保存到java代码块上 可以去这个方法最下面改

    }
}
