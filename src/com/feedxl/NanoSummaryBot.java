package com.feedxl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.InternetAddress;

import au.com.xprime.misc.mail.MailSender.Attachment;
import au.com.xprime.misc.mail.PostmarkMailSender;

import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.feedxl.tools.simpledb.SimpleDBExporter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NanoSummaryBot {

	public static final String EMAIL_TEMPLATE = "email.template.html";
	public static final String COUNT_KEY = "Count";
	public static final String TODAY = "TODAY";
	public static final String WEEK = "WEEK";
	public static final String MONTH = "MONTH";
	public static final String YEAR = "YEAR";
	public static final int MAX_NUMBER_LENGTH = 10;

	private static AmazonSimpleDBClient simpleDBConnection;
	private static String content;
	private static String profileName;
	private static String regionName;
	private static Map<String, User> users;
	private static List<Attachment> attachments = new ArrayList<Attachment>();
	private static PostmarkMailSender mailSender;
	private static String[] recipients;
	private static String emailSubject;
	private static String fromAddress;

	public static void main(String[] args) throws IOException {
		if (args.length < 5) {
			showError();
			return;
		}
		
		init(args);
		updateDomainStats();	
		downloadUsers();		
		updateUserStats();    
		createCsvAttachment(getNewUsersToday(), getTime("yyyy-MM-dd")+".csv");
		createCsvAttachment(users.values(), "all_users.csv");
        sendEmail();
	}

	private static void createCsvAttachment(Collection<User> users,
			String fileName) throws IOException {
		if (users.size() == 0) {
			return;
		}
		File file = writeAsCsv(users, fileName);
        createAttachment(fileName, file);
	}

	private static void createAttachment(String fileName, File file) {
		au.com.xprime.misc.mail.AbstractMailSender.Attachment attachment = new au.com.xprime.misc.mail.AbstractMailSender.Attachment();
        attachment.setFile(file);
        attachment.setContentType("text/plain");
        attachment.setFilename(fileName);
        attachments.add(attachment);
	}

	private static File writeAsCsv(Collection<User> usersToday, String fileName)
			throws IOException {
		File file = new File(fileName);
        file.createNewFile();
        FileWriter fileWriter = new FileWriter(file);
        BufferedWriter writer = new BufferedWriter(fileWriter);
        for (User user : usersToday) {
        	writer.write(user.asCsv()+"\n");
        }
        writer.close();
		return file;
	}

	private static List<User> getNewUsersToday() {
		List<User> usersToday = new ArrayList<User>();
		String todayTimeStr = getTime("yyyy-MM-dd");
		for (User user : users.values()) {
			String userTime = user.getTime();
			if (userTime.startsWith(todayTimeStr)) {
        		usersToday.add(user);
        		break;
        	}
		}
		return usersToday;
	}

	private static void showError() {
		System.err.println("Invalid number of arguments");
		System.err.println("Five arguments expected - [aws profile name] [region name] [subject] [from address] [comma seperated to addreses]");
	}

	private static void updateUserStats() throws IOException,
			FileNotFoundException {
        updateUserCounts(false, "UR");
        updateUserCounts(true, "UC");
	}

	private static void updateDomainStats() throws IOException {
		updateDomainStat("NanoPrydesAccessLog", "PV");
		updateDomainStat("NanoPrydesRecommendation", "DR");
		updateDomainStat("NanoPrydesReport", "PS");
	}

	private static void init(String[] args) throws IOException {
		profileName = args[0];
		regionName = args[1];
		emailSubject = args[2];
		fromAddress = args[3];
		recipients = args[4].split(",");
		
		simpleDBConnection = new SimpleDBConnectionHelper()
				.createSimpleDBConnection(profileName, regionName);
		content = readEmailTemplate();
        mailSender = new PostmarkMailSender();
        mailSender.setPostmarkServerToken("5b4d9c57-8827-490b-babd-7f81346b9580");
	}

	private static void sendEmail() {
		System.out.println(content);
		for (String email : recipients) {
	        try {
				mailSender.sendStandardEmail(new InternetAddress(fromAddress), 
						new InternetAddress(email), 
						emailSubject, 
						content, 
						attachments,
						true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void downloadUsers() throws IOException,
			FileNotFoundException {
		SimpleDBExporter exporter = new SimpleDBExporter(regionName, "NanoPrydesReportRequest", profileName, "");
		String fileName = exporter.export();
        File file = new File(fileName);
        FileReader fileReader = new FileReader(file);
        BufferedReader reader = new BufferedReader(fileReader);
        String line;
        Gson gson = new GsonBuilder().create();
        users = new HashMap<String,User>();
        while((line = reader.readLine()) != null) {
        	Map<?, ?> item = gson.fromJson(line, Map.class);
			List<Map<String,String>> attributes = (List<Map<String, String>>) item.get("attributes");
        	User user = new User(attributes);
        	User existingUser = users.get(user.getEmail());
        	if (existingUser != null) {
        		existingUser.merge(user);
        	} else 
        		users.put(user.getEmail(), user);
        }
        reader.close();
	}

	private static void updateUserCounts(
			boolean filterContactable, String prefix) {
		updateContent(prefix, YEAR, getUserCount("yyyy", filterContactable));
        updateContent(prefix, MONTH, getUserCount("yyyy-MM", filterContactable));
        updateContent(prefix, TODAY, getUserCount("yyyy-MM-dd", filterContactable));
		updateContent(prefix, WEEK, getUserCountForThisWeek(filterContactable));
	}

	private static int getUserCountForThisWeek(
			boolean filterContactable) {
		int thisWeek = 0;
		String time = getTime("yyyy-MM");
		int weekStart = getWeekStart();
		int weekEnd = getWeekEnd();
		if (weekStart > weekEnd) weekStart = 1;
		for (int i = weekStart; i <= weekEnd; i++) {
			thisWeek += getUserCount(time + "-" + String.format("%02d", i), filterContactable);
		}
		return thisWeek;
	}
	
	private static int getUserCount(String pattern, boolean filterContactable) {
		int count = 0;
        String timeStr = getTime(pattern);
        for (User user : users.values()) {
        	String time = user.getTime();
			if (time.startsWith(timeStr) && (!filterContactable || user.isContactable())) {
        		count++;
        	}
        }
		return count;
	}	

	private static void updateDomainStat(String domain, final String prefix) throws IOException {
        int thisYear = getCountFor("yyyy", domain);
        int thisMonth = getCountFor("yyyy-MM", domain);
        int thisWeek = getThisWeekCountFor(domain);
        int today = getCountFor("yyyy-MM-dd", domain);
        
        updateContent(prefix, TODAY, today);
        updateContent(prefix, WEEK, thisWeek);
        updateContent(prefix, MONTH, thisMonth);
        updateContent(prefix, YEAR, thisYear);
    }

	private static int getThisWeekCountFor(String domain) {
		int thisWeek = 0;
		String time = getTime("yyyy-MM");
		int weekStart = getWeekStart();
		int weekEnd = getWeekEnd();
		if (weekStart > weekEnd) weekStart = 1;
		for (int i = weekStart; i <= weekEnd; i++) {
			thisWeek += getCount(domain, time + "-" + String.format("%02d", i));
		}
		return thisWeek;
	}

	private static int getWeekEnd() {
		Calendar cal2 = Calendar.getInstance();
		return cal2.get(Calendar.DAY_OF_MONTH);
	}

	private static int getWeekStart() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
		return cal.get(Calendar.DAY_OF_MONTH);
	}

	private static void updateContent(String prefix, String key, int value) {
		content = content.replace("%" + prefix + "_" + key + "%",
				String.format("%" + MAX_NUMBER_LENGTH + "d", value));
	}

	private static String readEmailTemplate() throws IOException {
		InputStream stream = NanoSummaryBot.class
				.getClassLoader()
				.getResourceAsStream(EMAIL_TEMPLATE);
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream));
		String content = "";
		String line;
		while ((line = reader.readLine()) != null) {
			content += (line + "\n");
		}
		stream.close();
		return content;
	}

	private static int getCountFor(String pattern, String domain) {
		String timeStr = getTime(pattern);
		return getCount(domain, timeStr);
	}

	private static String getTime(String pattern) {
		return new SimpleDateFormat(pattern).format(new Date());
	}

	private static int getCount(final String domain, String timeStr) {
		String selectExpression = "select count(*) from " + domain
				+ " where time like '" + timeStr + "%'";
		SelectRequest selectRequest = new SelectRequest(selectExpression);
		SelectResult result = simpleDBConnection.select(selectRequest);
		List<Attribute> attributes = result.getItems().get(0).getAttributes();
		for (Attribute attribute : attributes) {
			if (attribute.getName().equals(COUNT_KEY)) {
				return Integer.parseInt(attribute.getValue());
			}
		}
		return -1;
	}
}
