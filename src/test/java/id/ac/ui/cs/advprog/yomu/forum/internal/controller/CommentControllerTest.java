package id.ac.ui.cs.advprog.yomu.forum.internal.controller;

import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentResponse;
import id.ac.ui.cs.advprog.yomu.forum.internal.service.CommentService;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CommentService commentService;

	private static final String API_ENDPOINT = "/api/forum/comments";

	@Test
	void createComment_withValidRequest_shouldReturn201() throws Exception {
		CommentCreatedEvent event = new CommentCreatedEvent(
			"user1",
			"bacaan1",
			"root",
			"comment1",
			"Test content",
			Instant.now()
		);

		when(commentService.createComment(
			eq("user1"),
			eq("bacaan1"),
			eq("Test content"),
			eq("root")
		)).thenReturn(event);

		mockMvc.perform(post(API_ENDPOINT)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"userId": "user1",
					"bacaanId": "bacaan1",
					"commentContent": "Test content",
					"parentComment": "root"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.commentId").value("comment1"))
			.andExpect(jsonPath("$.userId").value("user1"));
	}

	@Test
	void createComment_withoutParentComment_shouldDefaultToRoot() throws Exception {
		CommentCreatedEvent event = new CommentCreatedEvent(
			"user1",
			"bacaan1",
			"root",
			"comment1",
			"Test content",
			Instant.now()
		);

		when(commentService.createComment(
			eq("user1"),
			eq("bacaan1"),
			eq("Test content"),
			anyString()
		)).thenReturn(event);

		mockMvc.perform(post(API_ENDPOINT)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"userId": "user1",
					"bacaanId": "bacaan1",
					"commentContent": "Test content"
				}
				"""))
			.andExpect(status().isCreated());
	}

	@Test
	void getComments_withoutFilter_shouldReturnAll() throws Exception {
		CommentResponse c1 = new CommentResponse(
			"comment1", "user1", "bacaan1", "root", "Content 1",
			Instant.now(), 0, 0, 0, 0, 0, 0, 0
		);

		when(commentService.listComments(null)).thenReturn(List.of(c1));

		mockMvc.perform(get(API_ENDPOINT))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].commentId").value("comment1"))
			.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void getComments_filterByBacaanId_shouldReturnFiltered() throws Exception {
		CommentResponse c1 = new CommentResponse(
			"comment1", "user1", "bacaan1", "root", "Content 1",
			Instant.now(), 0, 0, 0, 0, 0, 0, 0
		);

		when(commentService.listComments("bacaan1")).thenReturn(List.of(c1));

		mockMvc.perform(get(API_ENDPOINT + "?bacaanId=bacaan1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].bacaanId").value("bacaan1"));
	}

	@Test
	void getCommentsTree_shouldReturnTreeStructure() throws Exception {
		CommentResponse root = new CommentResponse(
			"comment1", "user1", "bacaan1", "root", "Root",
			Instant.now(), 0, 0, 0, 0, 0, 0, 0
		);

		when(commentService.listCommentsTree(null)).thenReturn(List.of(root));

		mockMvc.perform(get(API_ENDPOINT + "/tree"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].commentId").value("comment1"));
	}

	@Test
	@WithMockUser(username = "user1")
	void addReaction_withValidRequest_shouldReturn200() throws Exception {
		CommentResponse updated = new CommentResponse(
			"comment1", "user1", "bacaan1", "root", "Content",
			Instant.now(), 1, 0, 0, 0, 0, 0, 0
		);

		when(commentService.addReaction("comment1", "upvote")).thenReturn(null);
		when(commentService.getComment("comment1")).thenReturn(updated);

		mockMvc.perform(post(API_ENDPOINT + "/comment1/reactions")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"reactionType": "upvote"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.commentId").value("comment1"));
	}

	@Test
	@WithMockUser(username = "user1")
	void addReaction_withMultipleTypes_shouldAllWork() throws Exception {
		CommentResponse updated = new CommentResponse(
			"comment1", "user1", "bacaan1", "root", "Content",
			Instant.now(), 0, 0, 1, 1, 1, 1, 0
		);

		when(commentService.getComment("comment1")).thenReturn(updated);

		mockMvc.perform(post(API_ENDPOINT + "/comment1/reactions")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"reactionType": "thumbs_up"
				}
				"""))
			.andExpect(status().isOk());
	}

	@Test
	void addReaction_withoutAuthentication_shouldReturn401() throws Exception {
		mockMvc.perform(post(API_ENDPOINT + "/comment1/reactions")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"reactionType": "upvote"
				}
				"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createComment_withMissingField_shouldReturn400() throws Exception {
		mockMvc.perform(post(API_ENDPOINT)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"userId": "user1",
					"bacaanId": "bacaan1"
				}
				"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "user1", roles = "USER")
	void updateComment_withValidRequest_shouldUpdate() throws Exception {
		mockMvc.perform(put(API_ENDPOINT + "/comment1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"commentContent": "Updated content"
				}
				"""))
			.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "user1", roles = "USER")
	void deleteComment_withValidRequest_shouldDelete() throws Exception {
		mockMvc.perform(delete(API_ENDPOINT + "/comment1"))
			.andExpect(status().isOk());
	}

	@Test
	void deleteComment_withoutAuthentication_shouldReturn401() throws Exception {
		mockMvc.perform(delete(API_ENDPOINT + "/comment1"))
			.andExpect(status().isUnauthorized());
	}
}
