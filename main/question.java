import io.github.furstenheim.CopyDown;
import io.github.furstenheim.Options;
import io.github.furstenheim.OptionsBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.request;
import util.setting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * @作者 Brown
 * @日期 2023/6/19 19:03
 */
public class question {
    public String id;
    public String title;
    public String titleSlug;//英文标题
    public String difficulty;//难易程度
    public String contents;//内容
    public String code;//代码
    public String status;//状态
    public boolean paid;//是否需要付款
    public String tags;//标签
    public HashMap<String, String> templates; //模板代码
    public ArrayList<question> similar;//相似题目

    public int row;//在xlxs的位置 索引
    private static final String mkFormat;//起的markdown名字格式
    private static final util.setting setting;
    private static final String onlyTemplates;
    private static final String url;
    private static final String markdown_path;
    private static final Random ra;
    //创建请求工具
    private util.request request;

    static {
        setting = new setting();
        url = setting.getConf("request.url");
        markdown_path = setting.getConf("question.markdown_path");
        mkFormat = setting.getConf("question.markdown_format");
        onlyTemplates = setting.getConf("question.onlyTemplates");
        ra = new Random();
    }


    public question() {
        this("", "");
    }

    public question(String titleSlug) {
        this(titleSlug, "");
    }

    public question(String titleSlug, String code) {
        this.titleSlug = titleSlug;
        this.code = code;
    }

    //主要给allQuestion设置的
    public question(request request) {
        this("", "");
        this.request = request;
    }

    public boolean getData() throws IOException, JSONException, InterruptedException {

        if (titleSlug == null || titleSlug.equals("")) {
            System.out.println("请配置题目");
            return false;
        }
        if (titleSlug.matches("\\d+")) {
            System.out.println("题目不应该全部为数字");
            return false;
        }

        if (url == null || url.equals("")) {
            System.out.println("请配置题目请求url");
            return false;
        }

        System.out.println("正在获取 " + titleSlug + " 相关数据");
        //如果一开始没有传递request 那么就创建它
        if (request == null) request = new request(url + "problems/" + titleSlug + "/description/");
        //查询并写入
        JSONObject data = request.start(question_body.formatted(titleSlug)).getJSONObject("data");
        if (data.getString("question").equals("null")) {
            System.out.println("题目没有查询到 请检查输入的题目");
            return false;
        }
        JSONObject questiondata = data.getJSONObject("question");
        setData(questiondata);
        System.out.println(titleSlug + " 数据获取成功");
        return true;
    }


    private void setData(JSONObject data) throws JSONException {
        title = data.getString("translatedTitle");
        titleSlug = data.getString("titleSlug");
        difficulty = data.getString("difficulty");
        if (difficulty.equals("Easy")) difficulty = "简单";
        else difficulty = difficulty.equals("Hard") ? "困难" : "中等";
        paid = data.getBoolean("isPaidOnly");
        if (!data.has("questionFrontendId")) return;
        // id
        id = data.getString("questionFrontendId");
        //内容
        contents = data.getString("translatedContent");
        //状态
        status = data.getString("status");
        if (status.equals("ac")) status = "已解答";
        else status = status.equals("null") ? "未开始" : "尝试过";
        //标签
        StringBuilder sb = new StringBuilder();
        JSONArray tagsJSON = data.getJSONArray("topicTags");
        if (tags == null) tags = "";
        for (int i = 0; i < tagsJSON.length(); i++) {
            String tag = tagsJSON.getJSONObject(i).getString("translatedName");
            sb.append(tag).append(',');
        }
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        tags = sb.toString();
        //模板
        templates = new HashMap<>();
        JSONArray templatesJSON = data.getJSONArray("codeSnippets");
        for (int i = 0; i < templatesJSON.length(); i++) {
            JSONObject info = templatesJSON.getJSONObject(i);
            //这里之保存小写名字 去掉Slug首字母会大写 个别编程语言会变 csharp->C#
            templates.put(info.getString("langSlug"), info.getString("code"));
        }
        //相似题目
        similar = new ArrayList<>();
        JSONArray similarJSON = new JSONArray(data.getString("similarQuestions"));
        for (int i = 0; i < similarJSON.length(); i++) {
            JSONObject info = similarJSON.getJSONObject(i);
            question p = new question();
            p.setData(info);
            similar.add(p);
        }

    }


    //获取提交过的代码 并将最新一条提交的AC代码存储到code变量中
    public void getSubmit() throws IOException, JSONException, InterruptedException {
        System.out.printf("正在获取%s.%s AC代码\n", id, title);
        if (request == null) request = new request(url + "problems/" + titleSlug + "/submissions//");
        //得到题目提交信息
        JSONArray submit_array = request.start(submit_body.formatted(titleSlug))
                .getJSONObject("data")
                .getJSONObject("submissionList")
                .getJSONArray("submissions");
        //获取最新AC的java代码
        for (int i = 0; i < submit_array.length(); i++) {
            JSONObject info = submit_array.getJSONObject(i);
            String submit_status = info.getString("statusDisplay");
            String submit_lang = info.getString("lang");
            if (submit_status.equals("Accepted") && submit_lang.equals("java")) {
                String submitId = info.getString("id");
                //url为  url + "submissions/detail/" + submitId
                Thread.sleep(ra.nextInt(300) + 100);
                JSONObject data = request.start(detail_body.formatted(submitId))
                        .getJSONObject("data").getJSONObject("submissionDetail");
                code = data.getString("code");
                System.out.println("AC代码寻找成功");
                return;
            }
        }
        System.out.println("并没有找到AC代码");
    }

    //生成markdown文件
    public boolean writeToMarkdown() {
        if (contents.equals("")) {
            System.out.println("并没有获取到相关信息,请获取题目信息后在保存!");
            return false;
        }
        if (onlyTemplates.equals("")) {
            System.out.println("请至少配置一种需要保存模板的语言");
            return false;
        }
        if (mkFormat.equals("")) {
            System.out.println("请配置markdown文件命名格式");
            return false;
        }
        if (markdown_path.equals("")) {
            System.out.println("请配置存放路径");
            return false;
        }
        File directory = new File(markdown_path);
        if (!directory.exists() && !directory.mkdirs()) {
            System.out.println("创建markdown存储路径失败 请检查配置");
            return false;
        }

        String mkName = getmkName(this);
        File file = new File(markdown_path + mkName);
        if (file.exists()) {
            System.out.println(file.getName() + "已经存在 已经将其覆盖");
        }
        System.out.println("正在创建 " + file.getName() + " 文件");
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            //添加标题和难度
            writer.write("## %s.%s\n**难度：** ".formatted(id, title));
            HashMap<String, String> colour = new HashMap<>();
            colour.put("简单", "<span style=\"color: rgba(0 175 155);\">简单</span>");
            colour.put("中等", "<span style=\"color: rgba(255 184 0);\">中等</span>");
            colour.put("困难", " <span style=\"color: rgba(255 45 85);\">困难</span>");
            writer.write(colour.get(difficulty));
            writer.write("\n\n");

            //添加原题链接
            writer.write("[%s](%s)\n\n".formatted(title, url + "problems/" + titleSlug));
            writer.write("---\n");

            //添加内容
            writer.write(HtmltoMarkdown(contents.replace("<sup>", "^"))
                    .replace("**输出", "\n**输出")
                    .replace("**解释", "\n**解释"));//正确翻译10^9
            writer.write("\n\n---\n\n");
            //添加 状态 题目标题 标签
            writer.write("**状态:** %s\n\n".formatted(status));
            writer.write("**题目标题:** %s\n\n".formatted(titleSlug));
            writer.write("**标签:** %s\n\n".formatted(tags.replace(",", " ")));//写入标签的时候以空格分开
            //添加 相关题目
            if (similar.size() > 0) {
                writer.write("**相关题目**\n");
                for (question problem : similar)
                    writer.write("+ [%s](%s) %s\n".formatted(problem.title, url + problem.titleSlug, colour.get(problem.difficulty)));
            }


            writer.write("## 题解\n");
            String[] ls = onlyTemplates.split(",");
            boolean java = false;
            if (code.length() > 0) {
                writer.write("`java`\n``` java\n%s\n```\n\n".formatted(code));
                java = true;
            }
            for (String l : ls)
                if (!l.equals("java") || !java)
                    writer.write("`%s`\n``` %s\n %s\n```\n\n".formatted(l, l, templates.get(l)));

            writer.close();
            System.out.println(file.getCanonicalPath() + " 创建成功");
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    // 将获取到的内容从HTML格式转换为Markdown格式
    private String HtmltoMarkdown(String content) {
        Options options = OptionsBuilder.anOptions().withBr("-").build();
        return new CopyDown(options).convert(content.replace("<sup>", "^"));
    }

    public static String getmkName(question q) {
        String name = mkFormat;
        if (q.title.equals("")) name = name.replace("${title}", "${titleSlug}");
        name = name.replace("${id}", q.id)
                .replace("${difficulty}", q.difficulty)
                .replace("${title}", q.title)
                .replace("${titleSlug}", q.titleSlug)
                .replace("${contents}", q.contents)
                .replace("${status}", q.status)
                .replace("${tags}", q.tags);
        if (q.paid) name = name.replace("${paid}", "true");
        else name = name.replace("${paid}", "false");
        return name + ".md";
    }


    private final static String question_body = """
            query{
              question(titleSlug:"%s") {
                questionFrontendId
                translatedTitle
                titleSlug
                difficulty
                translatedContent
                status
                isPaidOnly
                topicTags {
                  name
                  slug
                  translatedName
                }
                codeSnippets {
                  lang
                  langSlug
                  code
                }
                similarQuestions
              }
            }
            """;
    private final static String submit_body = """
            query submissions($lastKey: String) {
              submissionList(offset: 0, limit: 40, lastKey: $lastKey, questionSlug: "%s") {
                submissions {
                  id
                  statusDisplay
                  lang
                  runtime
                  timestamp
                  url
                  isPending
                  memory
                }
              }
            }
            """;
    private static final String detail_body = """
            query {
              submissionDetail(submissionId: %s) {
                id
                code
                runtime
                memory
                rawMemory
                statusDisplay
                timestamp
                lang
                isMine
                sourceUrl
              }
            }
            """;


}
