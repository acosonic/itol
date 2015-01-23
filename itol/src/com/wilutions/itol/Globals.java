package com.wilutions.itol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import com.wilutions.com.BackgTask;
import com.wilutions.com.reg.DeclRegistryValue;
import com.wilutions.com.reg.Registry;
import com.wilutions.itol.db.IdName;
import com.wilutions.itol.db.IssueService;
import com.wilutions.itol.db.IssueServiceFactory;
import com.wilutions.itol.db.PasswordEncryption;
import com.wilutions.itol.db.Property;
import com.wilutions.itol.db.PropertyClass;
import com.wilutions.itol.db.PropertyClasses;
import com.wilutions.itol.db.impl.IssueServiceFactory_JS;

public class Globals {

	private static ItolAddin addin;
	private static MailExport mailExport = new MailExport();
	private static Globals globalData = new Globals();
	private static ResourceBundle resb;
	private static IssueService issueService;
	private static Registry registry;

	@DeclRegistryValue
	private String serviceFactoryClass;
	
	@DeclRegistryValue
	private List<Property> configProps;

	protected static void setThisAddin(ItolAddin addin) {
		Globals.addin = addin;
	}
	
	public static void printAssignees1() {
		try {
			List<IdName> assignees = getIssueService().getAssignees(null);
			System.out.println("assignees=" + assignees);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static Registry getRegistry() {
		if (registry == null) {
			registry = new Registry("Issue Tracker for Outlook", "WILUTIONS");
		}	
		return registry;
	}

	public static void initIssueService() throws IOException {
		
		// Required for PasswordEncryption.decrypt 
		com.sun.org.apache.xml.internal.security.Init.init();
		
		readData();
		
		if (globalData.serviceFactoryClass != null && globalData.serviceFactoryClass.length() != 0) {
			try {
				Class<?> clazz = Class.forName(globalData.serviceFactoryClass);
				IssueServiceFactory fact = (IssueServiceFactory)clazz.newInstance();
				issueService = fact.getService();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		
		issueService = new IssueServiceFactory_JS().getService(); 
		
		BackgTask.run(() -> {
			try {
				issueService.setConfig(globalData.configProps);
				System.out.println("Issue service initialized.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		
		
	}
	
	private static void readData() {
		getRegistry().readFields(globalData);
		
		if (globalData.serviceFactoryClass == null) {
			globalData.serviceFactoryClass = IssueServiceFactory_JS.class.getName();
		}
		
		if (globalData.configProps == null) {
			globalData.configProps = new ArrayList<Property>();
		}

		for (Property configProp : globalData.configProps) {
			PropertyClass propClass = PropertyClasses.getDefault().get(configProp.getId());
			if (propClass != null && propClass.getType() == PropertyClass.TYPE_PASSWORD) {
				try {
					configProp.setValue(PasswordEncryption.decrypt((String)configProp.getValue()));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static void writeData() {
		for (Property configProp : globalData.configProps) {
			PropertyClass propClass = PropertyClasses.getDefault().get(configProp.getId());
			if (propClass != null && propClass.getType() == PropertyClass.TYPE_PASSWORD) {
				try {
					configProp.setValue(PasswordEncryption.encrypt((String)configProp.getValue()));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		getRegistry().writeFields(globalData);
	}
	
	public static void setConfig(List<Property> configProps) throws IOException {
		globalData.configProps = configProps;
		writeData();
		readData();
		issueService = new IssueServiceFactory_JS().getService(); 
		issueService.setConfig(configProps);
	}

	public static ItolAddin getThisAddin() {
		return addin;
	}

	public static MailExport getMailExport() {
		return mailExport;
	}

	public static IssueService getIssueService() throws IOException {
		if (issueService == null) {
			throw new IOException("Issue service not initialized.");
		}
		return issueService;
	}

	public static ResourceBundle getResourceBundle() {
		if (resb == null) {
			try {
				ClassLoader classLoader = Globals.class.getClassLoader();
				InputStream inputStream = classLoader.getResourceAsStream("com/wilutions/itol/res_en.properties");
				resb = new PropertyResourceBundle(inputStream);
			} catch (IOException e) {
				e.printStackTrace();
				try {
					resb = new PropertyResourceBundle(new ByteArrayInputStream(new byte[0]));
				} catch (IOException never) {
				}
			}
		}
		return resb;
	}
}
