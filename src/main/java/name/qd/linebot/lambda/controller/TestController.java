package name.qd.linebot.lambda.controller;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.CallbackRequest;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;

@RestController
@RequestMapping("/test")
public class TestController {
	private Logger logger = LoggerFactory.getLogger(TestController.class);
	private LineMessagingClient lineMessagingClient;
	private ObjectMapper objectMapper;
	private String LAMBDA_ENTRY_URL = System.getProperty("lambda_url");
	private RestTemplate restTemplate;
	
	@PostConstruct
	public void init() {
		lineMessagingClient = LineMessagingClient.builder(System.getProperty("token")).build();
		
		objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
		
		restTemplate = new RestTemplate();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
	}
	
	@RequestMapping(value = "", method = RequestMethod.POST)
	public ResponseEntity<Void> lineMessageEntry(@RequestBody String text) {
		MessageEvent<TextMessageContent> event = parseToTextMessageContent(text);
		
		if(event != null) {
			String lambdaResult;
			try {
				lambdaResult = sendToLambda(event.getMessage().getText());
				if(lambdaResult != "") {
					sendReply(event.getReplyToken(), lambdaResult);
				}
			} catch (MalformedURLException | URISyntaxException e) {
				logger.error("Call lambda failed.", e);
			}
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private MessageEvent<TextMessageContent> parseToTextMessageContent(String text) {
		try {
			CallbackRequest callbackRequest = objectMapper.readValue(text, CallbackRequest.class);
			
	        if(callbackRequest.getEvents().size() > 0) {
	        	
	        	Event e = callbackRequest.getEvents().get(0);
	        	if (e instanceof MessageEvent) {
	        		MessageEvent event = (MessageEvent) e;
	                MessageContent messageContent = event.getMessage();
	                if (messageContent instanceof TextMessageContent) {
	                	logger.info("instanceof TextMessageContent");
	                	MessageEvent<TextMessageContent> messageEvent = (MessageEvent<TextMessageContent>) e;
	                	return messageEvent;
	                }
	        	}
	        }
		} catch (JsonProcessingException e) {
			logger.error("Parse data failed: {}", text, e);
		}
		return null;
	}
	
	private String sendToLambda(String text) throws MalformedURLException, URISyntaxException {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("data", text);
		logger.info("Json: {}", node.toString());
		return restTemplate.postForObject(LAMBDA_ENTRY_URL, node.toString(), String.class);
	}
	
	private void sendReply(String replyToken, String message) {
		lineMessagingClient.replyMessage(new ReplyMessage(replyToken, new TextMessage(message)));
	}
}
