import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.request;
import util.setting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @作者 Brown
 * @日期 2023/6/19 21:23
 */
// 获取问题的基本信息 并保存下来
// 主要该信息在leetcode上面的属性名不一样

public class allQuestion {


    public static util.setting setting;
    public static String xlsx_path;

    public static String url;

    static {
        setting = new setting();
        xlsx_path = setting.getConf("question.xlsx_path");
        url = setting.getConf("request.url");
    }


    //获取leetcode题目的基本信息 有些题目的标题会有空格 也有的不会有中文题目
    // detail为是否保存详细信息 creatMk是否创建mk模板 onlymkAC 仅仅创建AC的题目markdown
    public void getAllQuestion(boolean detail, boolean creatMk, boolean onlymkAc) throws IOException, JSONException, InterruptedException {
        if (checkConf()) return;
        String[] Head = {"id", "标题", "英文标题", "难度", "状态", "需付款", "标签", "相似题目", "内容", "模板", "AC代码"};
        //xlsx
        File file = new File(xlsx_path);
        Workbook workbook;
        Sheet sheet;
        Row row;
        int xlsxLen;
        if (file.exists()) {
            workbook = new XSSFWorkbook(new FileInputStream(file));
            sheet = workbook.getSheetAt(0);
            xlsxLen = sheet.getLastRowNum() + 1;
        } else {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet();
            row = sheet.createRow(0);
            for (int i = 0; i < Head.length; i++) {
                if (!detail && i == 7) break;
                row.createCell(i).setCellValue(Head[i]);//添加第一行
            }
            xlsxLen = 1;
        }

        //step一次查询多少个(最大100) skip每次查询从第几题开始
        int step = 100, skip = xlsxLen - 1;//要去掉第一行 而且leetcode题目索引从0开始
        Random ra = new Random();
        request request = new request(url + "problemset/all/");
        while (true) {
            String body = String.format("""
                    query problemsetQuestionList($categorySlug: String) {
                      problemsetQuestionList(categorySlug: $categorySlug limit: %d skip: %d filters: {}) {
                        questions {
                          frontendQuestionId
                        }
                      }
                    }
                    """, step, skip);
            JSONArray array = request.start(body)
                    .getJSONObject("data")
                    .getJSONObject("problemsetQuestionList")
                    .getJSONArray("questions");
            if (array.length() == 0) break;//找不到为止
            System.out.format("开始写入%d~%d的数据\n", skip + 1, skip + Math.min(step, array.length()));
            question question = new question(request);
            for (int i = 0; i < array.length(); i++) {
                JSONObject data = array.getJSONObject(i);
                if (detail && !data.getBoolean("paidOnly")) {
                    question.titleSlug = data.getString("titleSlug");
                    question.getData();
                } else setData(question, data);//不用详细信息就可以直接读取基本信息了
                //写入到xlsx
                row = sheet.createRow(xlsxLen++);
                int c = 0;//第几列
                row.createCell(c++).setCellValue(question.id);
                row.createCell(c++).setCellValue(question.title);
                row.createCell(c++).setCellValue(question.titleSlug);
                row.createCell(c++).setCellValue(question.difficulty);
                row.createCell(c++).setCellValue(question.status);
                row.createCell(c++).setCellValue(question.paid);
                row.createCell(c++).setCellValue(question.tags);
                if (detail && !question.paid) {//此时的paid已经被重新赋值了
                    //相似
                    StringBuilder sb = new StringBuilder();
                    for (question q : question.similar) {
                        sb.append("[%s,%s,%s],".formatted(q.title, q.titleSlug, q.difficulty));
                    }
                    if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                    row.createCell(c++).setCellValue(sb.toString());
                    //内容
                    row.createCell(c++).setCellValue(question.contents);
                    //模板
                    sb = new StringBuilder();
                    for (Map.Entry<String, String> kv : question.templates.entrySet()) {
                        sb.append("[").append(kv.getKey()).append(",")
                                .append(kv.getValue()).append("],");
                    }
                    if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                    row.createCell(c++).setCellValue(sb.toString());
                    workbook.write(new FileOutputStream(xlsx_path));
                    //AC代码 不能用code的长度来判断 因为可能会保存上一题的代码
                    if (question.status.equals("已解答")) {
                        Thread.sleep(ra.nextInt(300) + 100);
                        question.getSubmit();
                        row.createCell(c).setCellValue(question.code);
                    } else question.code = "";//不是代码部分就清空
                    //生成markdown
                    if (creatMk && !question.paid && !onlymkAc || question.status.equals("已解答")) //允许写
                        question.writeToMarkdown();


                    int time = ra.nextInt(5000) + 1000;//随机睡眠0.1~1.5秒
                    System.out.format("休息 %.2f秒\n\n", 1.0 * time / 1000);
                    Thread.sleep(time);
                }
            }
            skip += array.length();
            int time = ra.nextInt(10001) + 5000;//随机睡眠5~15秒
            System.out.format("%d道题完成 休息 %.2f秒\n", array.length(), 1.0 * time / 1000);
            //写入excel文档 这个会等所有搞完才统一写入 要是需要获取详细信息建议每读取完就存一下 这样可以中途意外退出也会保存数据
            workbook.write(new FileOutputStream(xlsx_path));
            Thread.sleep(time);
        }
        workbook.close();
    }


    //读取xlsx表 将其信息存储下来
    public static HashMap<String, question> readAllQuestion() throws IOException {
        HashMap<String, question> map = new HashMap<>();
        if (checkConf()) return map;
        File file = new File(xlsx_path);
        if (!file.exists()) {
            System.out.println("xlsx 不存在 本次没有获取任何数据");
            return map;
        }

        //1.创建工作簿,使用excel能操作的这边都看看操作
        Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
        //2.得到表
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            question Q = new question();
            Q.row = i;
            Q.id = row.getCell(0).toString();
            Q.title = row.getCell(1).toString();
            Q.titleSlug = row.getCell(2).toString();
            Q.difficulty = row.getCell(3).toString();
            Q.status = row.getCell(4).toString();
            Q.paid = row.getCell(5).toString().equals("TRUE");
            Q.tags = row.getCell(6).toString();
            //相似
            Q.similar = new ArrayList<>();
            Cell cell = row.getCell(7);
            String similar = cell != null ? cell.toString() : "";
            if (!similar.equals("")) {
                String[] smis = similar.split("],\\[");
                smis[0] = smis[0].substring(1);
                smis[smis.length - 1] = smis[smis.length - 1].substring(0, smis[smis.length - 1].length() - 1);
                for (String sim : smis) {
                    String[] info = sim.split(",");
                    question que = new question();
                    que.title = info[0];
                    que.titleSlug = info[1];
                    que.difficulty = info[2];
                    Q.similar.add(que);
                }
            }
            //内容
            cell = row.getCell(8);
            Q.contents = cell != null ? cell.toString() : "";
            //模板
            Q.templates = new HashMap<>();
            cell = row.getCell(9);
            String templates = cell != null ? cell.toString() : "";
            if (!templates.equals("")) {
                String[] tmps = row.getCell(9).toString().split("],\\[");
                tmps[0] = tmps[0].substring(1);
                tmps[tmps.length - 1] = tmps[tmps.length - 1].substring(0, tmps[tmps.length - 1].length() - 1);
                for (String tem : tmps) {//这点很特殊
                    int st = tem.indexOf(',');
                    Q.templates.put(tem.substring(0, st), tem.substring(st + 1));
                }
            }
            //AC代码
            cell = row.getCell(10);
            Q.code = cell != null ? cell.toString() : "";
            map.put(Q.id, Q);
        }
        workbook.close();
        return map;
    }

    private static boolean checkConf() {
        if (xlsx_path.equals("")) {
            System.out.println("请配置存放路径");
            return true;
        }
        if (url.equals("")) {
            System.out.println("请配置首页的url");
            return true;
        }
        if (!xlsx_path.endsWith(".xlsx")) {
            System.out.println("请配置的路径最后一定以.xlsx为结尾");
            return true;
        }
        return false;
    }

    private void setData(question p, JSONObject data) throws JSONException {
        p.id = data.getString("frontendQuestionId");
        p.title = data.getString("titleCn");
        p.titleSlug = data.getString("titleSlug");
        p.difficulty = data.getString("difficulty");
        if (p.difficulty.equals("EASY")) p.difficulty = "简单";
        else p.difficulty = p.difficulty.equals("MEDIUM") ? "中等" : "困难";
        p.status = data.getString("status");
        if (p.status.equals("AC")) p.status = "已解答";
        else p.status = p.status.equals("TRIED") ? "尝试过" : "未开始";
        p.paid = data.getBoolean("paidOnly");
        StringBuilder sb = new StringBuilder();
        JSONArray tagsJSON = data.getJSONArray("topicTags");
        for (int i = 0; i < tagsJSON.length(); i++)
            sb.append(tagsJSON.getJSONObject(i).getString("nameTranslated")).append(',');
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        p.tags = sb.toString();
    }
}
