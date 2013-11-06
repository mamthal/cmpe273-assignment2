package edu.sjsu.cmpe.procurement;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.ws.rs.core.MediaType;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.domain.Book;
import edu.sjsu.cmpe.procurement.domain.BookList;

@Every("5mn")
public class ProcurementJob extends Job{
	
	Connection consumerConnection;
	Connection publisherConnection;
	@Override
	public void doJob(){
		String queueName = "/queue/86093.book.orders";
		String topic1 = "/topic/86093.book.computer";
		String topic2 = "/topic/86093.book.comics";
		String topic3 = "/topic/86093.book.management";
		String topic4 = "/topic/86093.book.selfimprovement";
		String apolloUser = "admin";
		String apolloPassword = "password";
		String apolloHost = "54.215.210.214";
		int apolloPort = 61613;
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + apolloHost + ":" + apolloPort);
		
		try{
			consumerConnection = factory.createConnection(apolloUser, apolloPassword);
			consumerConnection.start();
			Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination cdest = new StompJmsDestination(queueName);
			MessageConsumer consumer = consumerSession.createConsumer(cdest);
		
			ArrayList<Integer> isbn_list = new ArrayList<Integer>();
			Message msg = consumer.receive(2000);
			while(msg != null)
			{
				if( msg instanceof TextMessage ) {
					String body = ((TextMessage) msg).getText();
					System.out.println("Received message = " + body);
					String[] arr = body.split(":");
					isbn_list.add(Integer.parseInt(arr[1]));
				}
				msg = consumer.receive(2000);
			}
			Client client = new Client();
			if(isbn_list.size() > 0){
				WebResource webresource = client.resource("http://54.215.210.214:9000/orders");
			
				String input = "{\"id\":\"86093\",\"order_book_isbns\":[";
				for(int i = 0; i < isbn_list.size(); i++){
					if(i > 0)
						input = input + ",";
					input = input + isbn_list.get(i);
				}
				input = input + "]}";
				System.out.println(input);
				ClientResponse response = webresource.type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON_TYPE).acceptLanguage("en-US,en;q=0.8").post(ClientResponse.class, input);
				if(response.getStatus() == 200)
					System.out.println("HTTP Post Successful");
				else
					System.out.println("HTTP Post Failed");
			}
			
			publisherConnection = factory.createConnection(apolloUser, apolloPassword);
			publisherConnection.start();
			Session publisherSession = publisherConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
			Destination pdest = new StompJmsDestination(topic1);
			MessageProducer publisher1 = publisherSession.createProducer(pdest);
			publisher1.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

			pdest = new StompJmsDestination(topic2);
			MessageProducer publisher2 = publisherSession.createProducer(pdest);
			publisher2.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

			pdest = new StompJmsDestination(topic3);
			MessageProducer publisher3 = publisherSession.createProducer(pdest);
			publisher3.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

			pdest = new StompJmsDestination(topic4);
			MessageProducer publisher4 = publisherSession.createProducer(pdest);
			publisher4.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			
			WebResource webresource = client.resource("http://54.215.210.214:9000/orders/86093");
			ClientResponse response = webresource.type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
			String jsonstring = response.getEntity(String.class);
			System.out.println(jsonstring);
			Gson gson = new GsonBuilder().create();
			BookList booklist = gson.fromJson(jsonstring,BookList.class);
			List<Book> books_list = booklist.getbooks();
			for(int i = 0; i < books_list.size(); i++){
				String data = books_list.get(i).getIsbn() + ":" + books_list.get(i).getTitle() +":" + books_list.get(i).getCategory() + ":" +books_list.get(i).getCoverimage() + "";
				TextMessage publish_msg = publisherSession.createTextMessage(data);
				publish_msg.setLongProperty("id", System.currentTimeMillis());
				if(books_list.get(i).getCategory().equals("computer")){
					publisher1.send(publish_msg);
				}
				else if(books_list.get(i).getCategory().equals("comics")){
					publisher2.send(publish_msg);
				}
				else if(books_list.get(i).getCategory().equals("management")){
					publisher3.send(publish_msg);
				}
				else if(books_list.get(i).getCategory().equals("selfimprovement")){
					publisher4.send(publish_msg);
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			try {
				consumerConnection.close();
				publisherConnection.close();
			} catch (JMSException e) {
				e.printStackTrace();
			}
		}
	}
}