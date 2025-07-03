package com.example.dust.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DustService {

    @Value("${dust.api.key}")
    private String API_KEY;

    @Tool(name="getWeeklyDustByDate", description = "날짜로 대기질 전망과 주간예보 정보를 조회합니다. 날짜 형식: yyyy-MM-dd")
    public String getWeeklyDustByDate(String date) {
        try {
            // API URL 구성
            String apiUrl = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMinuDustWeekFrcstDspth"
                + "?serviceKey=" + API_KEY
                + "&returnType=json"
                + "&numOfRows=100"
                + "&pageNo=1"
                + "&searchDate=" + normalizeDate(date);

            log.info("API 요청 URL: {}", apiUrl);

            // HttpURLConnection 사용하여 API 호출
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            log.info("응답 코드: {}", responseCode);

            BufferedReader in;
            if (responseCode >= 200 && responseCode < 300) {
                in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }

            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String json = response.toString();
            log.info("API 응답: {}", json);

            // JSON이 아닐 경우(HTML 등) 예외 처리
            String jsonTrim = json.trim();
            if (!(jsonTrim.startsWith("{") || jsonTrim.startsWith("["))) {
                return "API에서 JSON이 아닌 응답을 반환했습니다. (예: 인증 오류, 서버 오류 등)\n응답 내용: " + jsonTrim;
            }

            if (responseCode >= 400) {
                return "API 오류 응답 (코드: " + responseCode + "): " + json;
            }

            // JSON 파싱
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode responseNode = root.path("response");
            JsonNode headerNode = responseNode.path("header");
            String resultCode = headerNode.path("resultCode").asText();
            String resultMsg = headerNode.path("resultMsg").asText();

            log.info("응답 코드: {}, 메시지: {}", resultCode, resultMsg);

            if (!"00".equals(resultCode) && !resultMsg.contains("NORMAL") && !resultMsg.contains("정상")) {
                return "API 오류: " + resultMsg;
            }

            JsonNode bodyNode = responseNode.path("body");
            if (bodyNode.isMissingNode() || bodyNode.isNull()) {
                return "API 응답에 body 정보가 없습니다.";
            }

            JsonNode itemsNode = bodyNode.path("items");
            if (itemsNode.isMissingNode() || itemsNode.isNull()) {
                return "API 응답에 items 정보가 없습니다.";
            }

            // 정상 데이터 케이스: items가 1개 이상이고, 첫 번째 item에 gwthcnd(예보문) 또는 frcstOneCn(예보내용) 등이 존재
            if (itemsNode.isArray() && itemsNode.size() > 0) {
                JsonNode firstItem = itemsNode.get(0);
                if (firstItem.has("gwthcnd") || firstItem.has("frcstOneCn")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("📅 발표일: ").append(firstItem.path("presnatnDt").asText("정보 없음")).append("\n");
                    sb.append("\n");
                    sb.append("🔹 예보문: ").append(firstItem.path("gwthcnd").asText("정보 없음")).append("\n\n");
                    sb.append("[1일 후 예보 - ").append(firstItem.path("frcstOneDt").asText("정보 없음")).append("]\n");
                    sb.append(firstItem.path("frcstOneCn").asText("정보 없음")).append("\n\n");
                    sb.append("[2일 후 예보 - ").append(firstItem.path("frcstTwoDt").asText("정보 없음")).append("]\n");
                    sb.append(firstItem.path("frcstTwoCn").asText("정보 없음")).append("\n\n");
                    sb.append("[3일 후 예보 - ").append(firstItem.path("frcstThreeDt").asText("정보 없음")).append("]\n");
                    sb.append(firstItem.path("frcstThreeCn").asText("정보 없음")).append("\n\n");
                    sb.append("[4일 후 예보 - ").append(firstItem.path("frcstFourDt").asText("정보 없음")).append("]\n");
                    sb.append(firstItem.path("frcstFourCn").asText("정보 없음")).append("\n");
                    return sb.toString();
                }
            }

            // 데이터 없음 케이스: items가 1개 이상이지만 예보문/예보내용이 없음, 또는 items가 빈 배열
            int totalCount = bodyNode.path("totalCount").asInt(0);
            if (totalCount == 0 || !itemsNode.elements().hasNext()) {
                return "해당 날짜에 대한 미세먼지 주간예보 데이터가 없습니다.";
            } else {
                // 날짜만 나열된 경우
                StringBuilder sb = new StringBuilder();
                sb.append("해당 날짜에 대한 미세먼지 주간예보 데이터가 없습니다.\n");
                sb.append("[조회 가능 날짜 목록]\n");
                for (JsonNode item : itemsNode) {
                    sb.append("- ").append(item.path("presnatnDt").asText("날짜 정보 없음")).append("\n");
                }
                return sb.toString();
            }
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 중 오류", e);
            return "JSON 처리 중 오류가 발생했습니다: " + e.getMessage();
        } catch (Exception e) {
            log.error("API 호출 중 오류", e);
            return "API 호출 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private String normalizeDate(String date) {
        if (date.matches("\\d{8}")) {
            return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
        }
        return date;
    }
}