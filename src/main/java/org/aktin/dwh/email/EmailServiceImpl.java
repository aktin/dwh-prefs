package org.aktin.dwh.email;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.aktin.Preferences;
import org.aktin.dwh.EmailService;
import org.aktin.dwh.PreferenceKey;

@Singleton
public class EmailServiceImpl implements EmailService {
	private static final Logger log = Logger.getLogger(EmailService.class.getName());

	@Inject
	private Preferences prefs;

	private Address[] emailRecipients;
	private Address[] replyTo;

	private Session mailSession;
	private Locale locale;

	@PostConstruct
	public void initialize(){
		// load mail session
		String jndiName = prefs.get(PreferenceKey.emailSession);
		log.info("Using mail session "+jndiName);
		try {
			InitialContext ctx = new InitialContext();
			mailSession = (Session)ctx.lookup(jndiName);
		} catch (NamingException e) {
			throw new IllegalStateException("Unable to load email session", e);
		}

		// default recipients
		try {
			emailRecipients = InternetAddress.parse(prefs.get(PreferenceKey.email));
			// reply to address
			replyTo = InternetAddress.parse(prefs.get(PreferenceKey.emailReplyTo));
		} catch (AddressException e) {
			throw new IllegalStateException("Error parsing email addresses from preferences", e);
		}
		// determine language
		String langTag = prefs.get(PreferenceKey.languageTag);
		if( langTag == null ){
			langTag = "de-DE"; // default to German
		}
		locale = Locale.forLanguageTag(langTag);
		log.info("Using locale "+locale);

	}

	@Override
	public Locale getLocale() {
		return locale;
	}

	@Override
	public void sendEmail(String subject, String content) throws IOException{
		try {
			MimeMessage msg = createMessage(subject);
			MimeMultipart mp = new MimeMultipart();
			// add text body part
			MimeBodyPart bp = new MimeBodyPart();
			bp.setText(content.toString(), "UTF-8");
			mp.addBodyPart(bp);
			msg.setContent(mp);
			Transport.send(msg);
		} catch (MessagingException e) {
			throw new IOException("Unable to send email: "+subject,e);
		}
	}

	@Override
	public void sendEmail(String subject, String content, DataSource attachment) throws IOException {
		try {
			MimeMessage msg = createMessage(subject);
			MimeMultipart mp = new MimeMultipart();
			// add text body part
			MimeBodyPart bp = new MimeBodyPart();
			bp.setText(content.toString(), "UTF-8");
			mp.addBodyPart(bp);
			// set attachment
			bp = new MimeBodyPart();
			bp.setFileName(attachment.getName());
			bp.setDataHandler(new DataHandler(attachment));
				mp.addBodyPart(bp);
			msg.setContent(mp);
			Transport.send(msg);
		} catch (MessagingException e) {
			throw new IOException("Unable to send email: "+subject,e);
		}
	}

	private MimeMessage createMessage(String subject) throws MessagingException{
		return createMessage(subject, this.emailRecipients);
	}
	
	private MimeMessage createMessage(String subject, Address[] recipients) throws MessagingException{
		MimeMessage msg = new MimeMessage(mailSession);
		msg.setFrom();
		msg.setRecipients(RecipientType.TO, recipients);
		msg.setReplyTo(replyTo);
		msg.setSubject(subject, "UTF-8");
		msg.setSentDate(new Date());
		return msg;
	}
	

}
