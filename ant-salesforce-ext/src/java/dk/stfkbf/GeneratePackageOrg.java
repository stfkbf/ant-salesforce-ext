package dk.stfkbf;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.tools.ant.Task;

import com.sforce.soap.metadata.DescribeMetadataObject;
import com.sforce.soap.metadata.DescribeMetadataResult;
import com.sforce.soap.metadata.FileProperties;
import com.sforce.soap.metadata.ListMetadataQuery;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.Package;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.bind.TypeMapper;
import com.sforce.ws.parser.XmlOutputStream;

public class GeneratePackageOrg extends Task {
	private List<String> wildcardTypes = Arrays.asList(
			"AccountSettings", "ActivitiesSettings", "AddressSettings", "ApexClass", "ApexComponent", 
			"ApexPage", "ApexTrigger", "AppMenu", "ApprovalProcess", "ArticleType", "AssignmentRules",
			"AutoResponseRules", "BusinessHoursSettings", "CallCenter", "CaseSettings", "ChatterAnswersSettings",
			"CompanySettings", "CompactLayout", "ConnectedApp", "ContractSettings", "CustomApplication", "CustomApplicationComponent",
			"CustomLabels", "CustomObjectTranslation", "CustomPageWebLink", "CustomSite", "CustomTab", "DataCategoryGroup",
			"EntitlementProcess", "EntitlementSettings", "EntitlementTemplate", "ExternalDataSource", "FieldSet", "FlexiPage",
			"Flow", "ForecastingSettings", "Group", "HomePageComponent", "HomePageLayout", "IdeasSettings", "InstalledPackage",
			"KnowledgeSettings", "Layout", "LiveAgentSettings", "LiveChatAgentConfig", "LiveChatButton", "LiveChatDeployment",
			"MilestoneType", "MobileSettings", "Network", "OrderSettings", "PermissionSet", "Portal", "PostTemplate",
			"Profile", "Queue", "QuickAction", "RecordType", "RemoteSiteSetting", "ReportType", "Role", "SamlSsoConfig",
			"Scontrol", "SecuritySettings", "SharingSet", "SiteDotCom", "Skill", "StaticResource", "Territory", "Translations", "Workflow", 
			"AccountSharingRules", "CampaignSharingRules", "CaseSharingRules", "ContactSharingRules", "LeadSharingRules", "OpportunitySharingRules", "UserSharingRules", "EntitlementProcess");	

	private List<String> ignoreTypes = Arrays.asList("InstalledPackage", "SynonymDictionary", "SiteDotCom");

	private List<String> ignoreMembers = Arrays.asList("SiteChangelist");
	
	private List<String> ignoreNamespaces = Arrays.asList("NVMContactWorld", "pca");

	private String username;
	private String password;
	private String authEndpoint;
	private String outputPath;

	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username.replace(" ", "");
	}
	public String getPassword() {
		return password.replace(" ", "");
	}
	public void setPassword(String password) {
		this.password = password.replace(" ", "");
	}
	public String getAuthEndpoint() {
		return authEndpoint;
	}
	public void setAuthEndpoint(String authEndpoint) {
		this.authEndpoint = authEndpoint;
	}
	public String getOutputPath() {
		return outputPath;
	}
	public void setOutputPath(String outputPath) {
		this.outputPath = outputPath;
	}


	public static void main(String[] args){
		GeneratePackageOrg builder = new GeneratePackageOrg();

		builder.setUsername(args[0]);
		builder.setPassword(args[1]);
		builder.setAuthEndpoint(args[2]);
		builder.setOutputPath(args[3]);
		builder.execute();
	}

	public void execute()  {
		try {
			ConnectorConfig metadataConfig = new ConnectorConfig();
			ConnectorConfig partnerConfig = new ConnectorConfig();

			System.out.println(this.username + ":<password>:" + this.authEndpoint);
			partnerConfig.setAuthEndpoint(this.authEndpoint);
			partnerConfig.setServiceEndpoint(this.authEndpoint);
			partnerConfig.setManualLogin(true);

			if (System.getProperty("http.proxyHost") != null && 
					System.getProperty("http.proxyPort") != null) {	
				SocketAddress addr = new InetSocketAddress(System.getProperty("http.proxyHost"), new Integer(System.getProperty("http.proxyPort")));
				Proxy proxy = new Proxy(Proxy.Type.HTTP, addr);

				metadataConfig.setProxy(proxy);
				partnerConfig.setProxy(proxy);
			}

			PartnerConnection partnerConnection = new PartnerConnection(partnerConfig);
			LoginResult loginResult = partnerConnection.login(this.username, this.password);

			metadataConfig.setSessionId(loginResult.getSessionId());
			metadataConfig.setServiceEndpoint(loginResult.getMetadataServerUrl());

			MetadataConnection metadataConnection = new MetadataConnection(metadataConfig);

			DescribeMetadataResult metadataDescribeResult = metadataConnection.describeMetadata(30.0);

			Map<String, List<String>> typeMembersByType = new HashMap<String, List<String>>();

			DescribeMetadataObject[] describeObjects = metadataDescribeResult.getMetadataObjects();
			String name;
			for(DescribeMetadataObject describeObject : describeObjects){
				if (ignoreTypes.contains(describeObject.getXmlName()))
					continue;

				if (wildcardTypes.contains(describeObject.getXmlName())){
					name = describeObject.getXmlName();
					ArrayList<String> typeNames = new ArrayList<String>();
					
					if (name.endsWith("SharingRules")){						
						typeNames.add(name.replace("SharingRules", "CriteriaBasedSharingRule"));	
						if (name.startsWith("User")){
							typeNames.add(name.replace("SharingRules", "MembershipSharingRule"));
						} else {
							typeNames.add(name.replace("SharingRules", "OwnerSharingRule"));
						}
					} else {
						typeNames.add(name);
					}
					
					for (String typeName : typeNames){
						List<String> typeMembers = new ArrayList<String>();
						typeMembers.add("*");
						typeMembersByType.put(typeName, typeMembers);
					
						System.out.println(describeObject.getXmlName() + " *");
					}
				} else {
					name = describeObject.getXmlName();

					if (describeObject.isInFolder())
						name = name + "Folder";

					if (describeObject.getXmlName().equals("EmailTemplate"))
						name = "EmailFolder";
										
					ArrayList<String> typeNames = new ArrayList<String>();
			
					if (name.endsWith("SharingRules")){						
						typeNames.add(name.replace("SharingRules", "CriteriaBasedSharingRule"));	
						if (name.startsWith("User")){
							typeNames.add(name.replace("SharingRules", "MembershipSharingRule"));
						} else {
							typeNames.add(name.replace("SharingRules", "OwnerSharingRule"));
						}
					} else {
						typeNames.add(name);
					}
					
					for (String typeName : typeNames){
						List<String> typeMembers = new ArrayList<String>();
						ListMetadataQuery query = new ListMetadataQuery();
						query.setType(typeName);
						ListMetadataQuery[] queries = {query};

						FileProperties[] results = metadataConnection.listMetadata(queries, 30.0);

						System.out.println(typeName + " " + results.length);

						for (FileProperties result : results){
							String namespace = "";
							
							if (result.getFullName().contains("__"))
								namespace = result.getFullName().split("__")[0];
							
							if (ignoreMembers.contains(result.getFullName()))
								continue;
							
							// Ignore documents belonging to installed packages
							// Other components should never change
							if (typeName.startsWith("Document") && ignoreNamespaces.contains(namespace))
									continue;
							
							typeMembers.add(result.getFullName());
							if (describeObject.isInFolder()){
								ListMetadataQuery folderQuery = new ListMetadataQuery();
								folderQuery.setType(describeObject.getXmlName());
								folderQuery.setFolder(result.getFullName());
								ListMetadataQuery[] folderQueries = {folderQuery};

								FileProperties[] folderResults = metadataConnection.listMetadata(folderQueries, 30.0);
								for (FileProperties folderResult : folderResults){
									typeMembers.add(folderResult.getFullName());
								}
							}
						}
						
						String translatedTypeName = typeName;
						
						if (typeName.equals("ReportFolder") || typeName.equals("DashboardFolder") || typeName.equals("DocumentFolder"))
							translatedTypeName = typeName.replace("Folder", "");
						
						if (typeName.equals("EmailFolder"))
							translatedTypeName = "EmailTemplate";
						
						if (typeMembers.size() > 0)
							typeMembersByType.put(translatedTypeName, typeMembers);
					}
					
				}    		    	    	        	
			}

			String packageManifestXml = generatePackage(typeMembersByType);			
			PrintWriter out = new PrintWriter(this.outputPath + "/package.xml");
			out.write(packageManifestXml);
			out.close();
		} catch (Exception e){
			// Do something meaningful here
			e.printStackTrace();
		}
	}

	public String generatePackage(Map<String, List<String>> typeMembersByType) throws Exception {
		String packageManifestXml = null;

		// Construct package manifest and files to deploy map by path
		Package packageManifest = new Package();    	
		packageManifest.setVersion("30.0"); // TODO: Make version configurable / auto
		List<PackageTypeMembers> packageTypeMembersList = new ArrayList<PackageTypeMembers>(); 

		for(String metadataType : typeMembersByType.keySet())
		{
			PackageTypeMembers packageTypeMembers = new PackageTypeMembers();
			packageTypeMembers.setName(metadataType);
			packageTypeMembers.setMembers((String[])typeMembersByType.get(metadataType).toArray(new String[0]));
			packageTypeMembersList.add(packageTypeMembers);
		}
		packageManifest.setTypes((PackageTypeMembers[]) packageTypeMembersList.toArray(new PackageTypeMembers[0]));	    	
		TypeMapper typeMapper = new TypeMapper();
		ByteArrayOutputStream packageBaos = new ByteArrayOutputStream();
		QName packageQName = new QName("http://soap.sforce.com/2006/04/metadata", "Package");
		XmlOutputStream xmlOutputStream = new XmlOutputStream(packageBaos, true);
		xmlOutputStream.setPrefix("", "http://soap.sforce.com/2006/04/metadata");
		xmlOutputStream.setPrefix("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		packageManifest.write(packageQName, xmlOutputStream, typeMapper);
		xmlOutputStream.close();
		packageManifestXml = new String(packageBaos.toByteArray());  	        

		return packageManifestXml;
	} 
}
