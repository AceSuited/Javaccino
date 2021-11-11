package com.emotie.api.diary;

import com.emotie.api.AcceptanceTest;
import com.emotie.api.auth.dto.LoginRequest;
import com.emotie.api.auth.dto.LoginResponse;
import com.emotie.api.diary.domain.Diary;
import com.emotie.api.diary.domain.DiaryIds;
import com.emotie.api.diary.dto.*;
import com.emotie.api.diary.repository.DiaryRepository;
import com.emotie.api.emotion.domain.Emotion;
import com.emotie.api.emotion.repository.EmotionRepository;
import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.emotie.api.auth.AuthAcceptanceTest.loginRequest;
import static com.emotie.api.diary.DiaryDataLoader.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

// TODO: 2021-08-06 실제로 단위 테스트 구현하기
@SuppressWarnings({"FieldCanBeLocal", "NonAsciiCharacters", "UnnecessaryLocalVariable", "SameParameterValue"})
@ActiveProfiles({"diaryDataLoader"})
@TestMethodOrder(MethodOrderer.DisplayName.class)
@Transactional
public class DiaryApiTest extends AcceptanceTest {
    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    /* Create: 다이어리 작성 */
    @Test
    @DisplayName("테스트 01.01: 다이어리 작성 시 [400]; 감정이 정해지지 않았을 경우")
    public void 작성_실패_BAD_REQUEST_1() {
        //given
        String accessToken = writerLogin();
        DiaryCreateRequest diaryCreateRequest = DiaryCreateRequest.builder()
                .emotion(null)
                .content(newContent)
                .isOpened(false)
                .build();

        //when
        ExtractableResponse<Response> response = diaryCreateRequest(accessToken, diaryCreateRequest);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 01.02: 다이어리 작성 시 [400]; 내용이 null일 경우")
    public void 작성_실패_BAD_REQUEST_2() {
        //given
        String accessToken = writerLogin();
        DiaryCreateRequest diaryCreateRequest = DiaryCreateRequest.builder()
                .emotion(diaryEmotion.getName())
                .content(" ")
                .isOpened(false)
                .build();

        //when
        ExtractableResponse<Response> response = diaryCreateRequest(accessToken, diaryCreateRequest);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 01.03: 다이어리 작성 시 [403]; 로그인하지 않았을 경우")
    public void 작성_실패_FORBIDDEN() {
        //given
        String accessToken = "";
        DiaryCreateRequest diaryCreateRequest = DiaryCreateRequest.builder()
                .emotion(diaryEmotion.getName())
                .content(newContent)
                .isOpened(false)
                .build();

        //when
        ExtractableResponse<Response> response = diaryCreateRequest(accessToken, diaryCreateRequest);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 01.04: 다이어리 작성 시 [404]; 존재하지 않는 감정일 경우")
    public void 작성_실패_BAD_REQUEST_3() {
        //given
        String accessToken = writerLogin();
        DiaryCreateRequest diaryCreateRequest = DiaryCreateRequest.builder()
                .emotion("우울")
                .content(newContent)
                .isOpened(false)
                .build();

        //when
        ExtractableResponse<Response> response = diaryCreateRequest(accessToken, diaryCreateRequest);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 01.05: 다이어리 작성 성공 [200]")
    public void 작성_성공_OK() {
        //given
        String accessToken = writerLogin();
        DiaryCreateRequest diaryCreateRequest = DiaryCreateRequest.builder()
                .emotion(diaryEmotion.getName())
                .content(newContent)
                .isOpened(false)
                .build();

        //when
        ExtractableResponse<Response> response = diaryCreateRequest(accessToken, diaryCreateRequest);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertDiaryAdded(1);
        assertDiaryContentExists(newContent);
        assertWriterEmotionDeepened(diaryEmotion.getName());

        updateWriterEmotionScores();
        diaryCount++;
    }

    /* Read: 다이어리 조회 */
    @Test
    @DisplayName("테스트 02.01: 다이어리 개별 조회 시 [403]; 비공개 게시물의 경우")
    public void 개별_조회_실패_FORBIDDEN() {
        //when
        ExtractableResponse<Response> response = diaryReadRequest(closedDiaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("테스트 02.02: 다이어리 개별 조회 시 [404]; 해당 게시물이 없을 경우")
    public void 개별_조회_실패_NOT_FOUND() {
        //when
        ExtractableResponse<Response> response = diaryReadRequest(invalidId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("테스트 02.03: 다이어리 개별 조회 성공 [200]; 일반적인 경우")
    public void 개별_조회_성공_OK() {
        //when
        ExtractableResponse<Response> response = diaryReadRequest(openedDiaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().jsonPath().get("content").equals(originalContent)).isTrue();
    }

    @Test
    @DisplayName("테스트 02.04: 다이어리 개별 조회 성공 [200]; 본인이 본인의 게시물 중 private 게시물을 보는 경우")
    public void 개별_조회_성공_OK_2() {
        //given
        String accessToken = writerLogin();

        //when
        ExtractableResponse<Response> response = diaryReadRequest(accessToken, closedDiaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().jsonPath().get("content").equals(originalContent)).isTrue();
    }

    @Test
    @DisplayName("테스트 03.01: 다이어리 전체 조회 시 [400]; 페이지 인덱스가 너무 크거나 작을 경우; 즉, 페이지 인덱스가 없을 경우")
    public void 전체_조회_실패_NOT_FOUND_1() {
        //given
        Integer pageNumber = Integer.MAX_VALUE;
        String accessToken = viewerLogin();
        String memberId = writerId;

        //when
        ExtractableResponse<Response> response = diaryReadAllRequest(accessToken, memberId, pageNumber);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("테스트 03.02: 다이어리 전체 조회 시 [404]; 해당하는 회원이 없을 경우")
    public void 전체_조회_실패_NOT_FOUND() {
        //given
        Integer pageNumber = 0;
        String accessToken = viewerLogin();
        String memberId = notExistMemberId;

        //when
        ExtractableResponse<Response> response = diaryReadAllRequest(accessToken, memberId, pageNumber);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("테스트 03.03: 다이어리 전체 조회 성공 [200]; 일반적인 경우")
    public void 전체_조회_성공_OK_1() {
        //given
        Integer pageNumber = 0;
        String accessToken = viewerLogin();
        String memberId = writerId;

        ExtractableResponse<Response> response = diaryReadAllRequest(accessToken, memberId, pageNumber);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertWellPaged(response, "90");
        assertThat(response.body().as(DiaryReadAllResponse.class).getDiaries()).allMatch(DiaryReadResponse::getIsOpened);
    }

    @Test
    @DisplayName("테스트 03.04: 다이어리 전체 조회 성공 [200]; 작성자의 경우")
    public void 전체_조회_성공_OK_2() {
        //given
        Integer pageNumber = 0;
        String accessToken = writerLogin();
        String memberId = writerId;

        //when
        ExtractableResponse<Response> response = diaryReadAllRequest(accessToken, memberId, pageNumber);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertWellPaged(response, "94");

        //given
        pageNumber = 9;

        //when
        response = diaryReadAllRequest(accessToken, memberId, pageNumber);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertWellPaged(response, "1");
    }

    /* Delete: 다이어리 삭제 */
    @Test
    @DisplayName("테스트 04.01: 다이어리 삭제 시 [403]; 로그인하지 않았을 경우")
    public void 삭제_실패_FORBIDDEN_1() {
        //given
        String accessToken = "";
        DiaryDeleteRequest request = getDiaryDeleteRequest(Set.of(openedDiaryId));

        //when
        ExtractableResponse<Response> response = diaryDeleteRequest(accessToken, request);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(diaryRepository.existsById(openedDiaryId)).isTrue();
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 04.02: 다이어리 삭제 시 [403]; 삭제를 요청한 사람과 작성자가 다를 경우")
    public void 삭제_실패_FORBIDDEN_2() {
        //given
        String accessToken = viewerLogin();
        DiaryDeleteRequest request = getDiaryDeleteRequest(Set.of(openedDiaryId));

        //when
        ExtractableResponse<Response> response = diaryDeleteRequest(accessToken, request);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(diaryRepository.existsById(openedDiaryId)).isTrue();
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 04.03: 다이어리 삭제 시 [404]; 삭제를 요청한 데이터 중 일부 혹은 전체가 없는 경우")
    public void 삭제_실패_NOT_FOUND() {
        //given
        String accessToken = writerLogin();
        DiaryDeleteRequest request = getDiaryDeleteRequest(Set.of(openedDiaryId, invalidId));

        //when
        ExtractableResponse<Response> response = diaryDeleteRequest(accessToken, request);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(diaryRepository.existsById(openedDiaryId)).isTrue();
        assertDiaryNotAdded();
        assertWriterScoreNotUpdated();
    }

    @Test
    @DisplayName("테스트 04.04: 다이어리 삭제 성공 [200]")
    public void 삭제_성공_OK() {
        //given
        String accessToken = writerLogin();
        DiaryDeleteRequest request = getDiaryDeleteRequest(Set.of(openedDiaryId));

        //when
        ExtractableResponse<Response> response = diaryDeleteRequest(accessToken, request);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(diaryRepository.existsById(openedDiaryId)).isFalse();
        assertWriterEmotionReduced(diaryEmotion.getName());

        updateWriterEmotionScores();
        diaryCount--;
    }

    private DiaryDeleteRequest getDiaryDeleteRequest(Set<Long> openedDiaryId) {
        return DiaryDeleteRequest.builder()
                .diaryId(openedDiaryId)
                .build();
    }

    /* 다이어리 신고 */
    @Test
    @DisplayName("테스트 05.01: 다이어리 신고 시 [403]; 로그인하지 않음")
    public void 다이어리_신고_실패_FORBIDDEN() {
        //given
        String accessToken = "";
        Integer diaryId = 0;

        //when
        ExtractableResponse<Response> response = diaryReportRequest(accessToken, diaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("테스트 05.02: 다이어리 신고 시 [404]; 해당 다이어리가 없는 경우")
    public void 다이어리_신고_실패_NOT_FOUND() {
        //given
        String accessToken = viewerLogin();
        Integer diaryId = 0;

        //when
        ExtractableResponse<Response> response = diaryReportRequest(accessToken, diaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("테스트 05.03: 다이어리 신고 성공 [200]; Not Report -> Report")
    public void 다이어리_신고_성공_OK_1() {
        //given
        String accessToken = viewerLogin();
        Integer diaryId = 0;

        //when
        ExtractableResponse<Response> response = diaryReportRequest(accessToken, diaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().as(DiaryReportResponse.class))
                .hasFieldOrPropertyWithValue("isReported", true);
    }

    @Test
    @DisplayName("테스트 05.04: 다이어리 신고 성공 [200]; Report -> Not Report")
    public void 다이어리_신고_성공_OK_2() {
        //given
        String accessToken = viewerLogin();
        Integer diaryId = 0;

        //when
        ExtractableResponse<Response> response = diaryReportRequest(accessToken, diaryId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().as(DiaryReportResponse.class))
                .hasFieldOrPropertyWithValue("isReported", false);
    }

    public static ExtractableResponse<Response> diaryCreateRequest(String accessToken, DiaryCreateRequest request) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .body(request)
                .contentType(APPLICATION_JSON_VALUE)
                .when().post("/diaries")
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryReadRequest(Long diaryId) {
        return RestAssured
                .given().log().all()
                .when().get("/diaries/{diaryId}", diaryId)
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryReadRequest(String accessToken, Long diaryId) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .when().get("/diaries/{diaryId}", diaryId)
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryReadAllRequest(
            String accessToken, String memberId, Integer pageNumber
    ) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .queryParam("page", pageNumber)
                .when().get("/diaries/user/{memberId}", memberId)
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryExportRequest(String accessToken, DiaryExportRequest request) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .body(request)
                .contentType(APPLICATION_JSON_VALUE)
                .when().post("/diaries/export")
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryExportAllRequest(
            String accessToken, DiaryExportAllRequest request
    ) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .body(request)
                .contentType(APPLICATION_JSON_VALUE)
                .when().post("/diaries/export_all")
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryDeleteRequest(String accessToken, DiaryDeleteRequest request) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .body(request)
                .contentType(APPLICATION_JSON_VALUE)
                .when().delete("/diaries")
                .then().log().all()
                .extract();
    }

    private static ExtractableResponse<Response> diaryReportRequest(String accessToken, Integer diaryId) {
        return RestAssured
                .given().log().all()
                .auth().oauth2(accessToken)
                .when().put("/diaries/report/{diaryId}", diaryId)
                .then().log().all()
                .extract();
    }

    public static String writerLogin() {
        LoginRequest request = LoginRequest.builder()
                .email(writerEmail)
                .password(password)
                .build();

        return loginRequest(request)
                .as(LoginResponse.class)
                .getAccessToken();
    }

    private static String viewerLogin() {
        LoginRequest request = LoginRequest.builder()
                .email(viewerEmail)
                .password(password)
                .build();

        return loginRequest(request)
                .as(LoginResponse.class)
                .getAccessToken();
    }

    private void assertDiaryNotAdded() {
        assertThat(diaryRepository.count()).isEqualTo(diaryCount);
    }

    private void assertDiaryAdded(Integer amount) {
        assertThat(diaryRepository.count()).isEqualTo(diaryCount + amount);
    }

    private void assertDiaryContentExists(String content) {
        assertThat(diaryRepository.findAll()).map(Diary::getContent).anySatisfy(
                it -> assertThat(it).isEqualTo(content)
        );
    }

    private void assertWriterScoreNotUpdated() {
        assertThat(emotionRepository.findAllByMember(writer)).allMatch(
                emotion -> writerEmotionScores.get(emotion.getName()).equals(emotion.getScore())
        );
    }

    private void assertWriterEmotionDeepened(String deepeningEmotionName) {
        assertThat(emotionRepository.findAllByMember(writer))
                .allSatisfy(
                        emotion -> {
                            if (emotion.getName().equals(deepeningEmotionName)) {
                                assertThat(emotion.getScore())
                                        .isGreaterThanOrEqualTo(writerEmotionScores.get(deepeningEmotionName));
                            } else {
                                assertThat(emotion.getScore())
                                        .isLessThanOrEqualTo(writerEmotionScores.get(deepeningEmotionName));
                            }
                        }
                );
    }

    private void assertWriterEmotionReduced(String reducingEmotionName) {
        assertThat(emotionRepository.findAllByMember(writer))
                .allSatisfy(
                        emotion -> {
                            if (emotion.getName().equals(reducingEmotionName)) {
                                assertThat(emotion.getScore())
                                        .isLessThanOrEqualTo(writerEmotionScores.get(reducingEmotionName));
                            } else {
                                assertThat(emotion.getScore())
                                        .isGreaterThanOrEqualTo(writerEmotionScores.get(emotion.getName()));
                            }
                        }
                );
    }

    private void updateWriterEmotionScores() {
        emotionRepository.findAllByMember(writer).forEach(
                emotion -> writerEmotionScores.put(emotion.getName(), emotion.getScore())
        );
    }

    private void assertWellPaged(ExtractableResponse<Response> response, String confirmedContent) {
        assertThat(response.body().as(DiaryReadAllResponse.class)).hasFieldOrProperty("diaries");
        assertThat(response.body().as(DiaryReadAllResponse.class).getDiaries().size()).isLessThanOrEqualTo(PAGE_SIZE);
        assertThat(response.body().as(DiaryReadAllResponse.class).getDiaries()).anyMatch(
                (diaryReadResponse) -> diaryReadResponse.getContent().contains(confirmedContent)
        );
    }
}
