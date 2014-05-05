package dk.stfkbf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.tools.ant.Task;

import com.sforce.soap.metadata.DescribeMetadataObject;
import com.sforce.soap.metadata.DescribeMetadataResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.Package;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.bind.TypeMapper;
import com.sforce.ws.parser.XmlOutputStream;

public class GeneratePackageFileset extends Task {
	private String username;
	private String password;
	private String authEndpoint;
	private String repositoryPath;
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
	public String getRepositoryPath() {
		return repositoryPath;
	}
	public void setRepositoryPath(String repositoryPath) {
		this.repositoryPath = repositoryPath;
	}
	public String getOutputPath() {
		return outputPath;
	}
	public void setOutputPath(String outputPath) {
		this.outputPath = outputPath;
	}
	
	public static void main(String[] args){
		GeneratePackageFileset builder = new GeneratePackageFileset();
		
		builder.setUsername(args[0]);
		builder.setPassword(args[1]);
		builder.setAuthEndpoint(args[2]);
		builder.setOutputPath(args[3]);
		builder.setRepositoryPath(args[4]);
		
		builder.execute();
	}
	
	public void execute()  {
		try
		{	  
			ConnectorConfig metadataConfig = new ConnectorConfig();
			ConnectorConfig partnerConfig = new ConnectorConfig();
			
			System.out.println(this.username + ":" + this.password + ":" + this.authEndpoint);
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

			// Prepare Salesforce metadata metadata for repository scan
			RepositoryScanResult repositoryScanResult = new RepositoryScanResult();
			RepositoryItem repositoryContainer = new RepositoryItem();
			repositoryContainer.repositoryItems = new ArrayList<RepositoryItem>();
			repositoryScanResult.metadataFolderBySuffix = new HashMap<String, DescribeMetadataObject>();
			
			DescribeMetadataResult metadataDescribeResult = metadataConnection.describeMetadata(30.0); // TODO: Make version configurable / auto
			for(DescribeMetadataObject describeObject : metadataDescribeResult.getMetadataObjects())
			{
				// Use Directory Name and Suffix to ensure uniqueness (for example for the site suffix which occurs in SiteDotCom and Sites
				repositoryScanResult.metadataFolderBySuffix.put(describeObject.getDirectoryName() + ":" + describeObject.getSuffix(), describeObject);
				
				if(describeObject.getMetaFile())
					repositoryScanResult.metadataFolderBySuffix.put(describeObject.getDirectoryName() + ":" + describeObject.getSuffix() + "-meta.xml", describeObject);
			}
    	
			scanRepository(repositoryPath, repositoryContainer, repositoryScanResult);	    		    	

			if(repositoryContainer.repositoryItems.size()>0){
				String packageManifestXml = generatePackage(repositoryContainer);
				PrintWriter out = new PrintWriter(this.outputPath + "/package.xml");
				out.write(packageManifestXml);
				out.close();
			}			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public String generatePackage(RepositoryItem repositoryContainer) throws Exception {    
		String packageManifestXml = null;
		Map<String, RepositoryItem> filesToDeploy = new HashMap<String, RepositoryItem>();
		Map<String, List<String>> typeMembersByType = new HashMap<String, List<String>>(); 

		// Construct package manifest and files to deploy map by path
		Package packageManifest = new Package();    	
		packageManifest.setVersion("30.0"); // TODO: Make version configurable / auto
		List<PackageTypeMembers> packageTypeMembersList = new ArrayList<PackageTypeMembers>(); 
		
		scanFilesToDeploy(filesToDeploy, typeMembersByType, repositoryContainer);
		
		for(String metadataType : typeMembersByType.keySet())
		{
			PackageTypeMembers packageTypeMembers = new PackageTypeMembers();
			packageTypeMembers.setName(metadataType);
			packageTypeMembers.setMembers((String[])typeMembersByType.get(metadataType).toArray(new String[0]));
			packageTypeMembersList.add(packageTypeMembers);
		}
		packageManifest.setTypes((PackageTypeMembers[]) packageTypeMembersList.toArray(new PackageTypeMembers[0]));	    	
		// Serialise it (better way to do this?)
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

	/**
	 * Scans the files the user selected they want to deploy and maps the paths and metadata types
	 * @param filesToDeploy
	 * @param typeMembersByType
	 * @param repositoryContainer
	 */
	private void scanFilesToDeploy(Map<String, RepositoryItem> filesToDeploy, Map<String, List<String>> typeMembersByType, RepositoryItem repositoryContainer)
	{
		for(RepositoryItem repositoryItem : repositoryContainer.repositoryItems)
		{
			if(repositoryItem.isDirectory())
			{
				// Scan into directory
				scanFilesToDeploy(filesToDeploy, typeMembersByType, repositoryItem);
			}
			else
			{
				// Map path to repository item
				filesToDeploy.put(repositoryItem.getPath(), repositoryItem);
				// Is this repository file a metadata file?
				Boolean isMetadataFile = repositoryItem.getName().endsWith(".xml");
				Boolean isMetadataFileForFolder = "dir".equals(repositoryItem.metadataSuffix);
				if(isMetadataFile) // Skip meta files
					if(!isMetadataFileForFolder) // As long as its not a metadata file for a folder
						continue;    			
				// Add item to list by metadata type for package manifiest generation
				List<String> packageTypeMembers = typeMembersByType.get(repositoryItem.metadataType);
				if(packageTypeMembers==null)
					typeMembersByType.put(repositoryItem.metadataType, (packageTypeMembers = new ArrayList<String>()));
				// Determine the component name
				String componentName = repositoryItem.getName();
				if(componentName.indexOf(".")>0) // Strip file extension?
					componentName = componentName.substring(0, componentName.indexOf("."));
				if(componentName.indexOf("-meta")>0) // Strip any -meta suffix (on the end of folder metadata file names)?
					componentName = componentName.substring(0, componentName.indexOf("-meta"));
				// Qualify the component name by its folder?
				if(repositoryItem.metadataInFolder)
				{
					// Parse the component folder name from the path to the item
					String[] folders = repositoryItem.getPath().split("/");
					String folderName = folders[folders.length-2];
					componentName = folderName + "/" + componentName;    
					
					//If the folder itself has not been added, add the folder.
					
					File folderFile = new File(repositoryItem.getPath().replace(repositoryItem.getName(), folderName + "-meta.xml"));
					
					if (!packageTypeMembers.contains(folderName) && folderFile.exists())
						packageTypeMembers.add(folderName);
				}
				packageTypeMembers.add(componentName);
			}
		}
	}

	private void scanRepository(String repositoryPath, RepositoryItem repositoryContainer, RepositoryScanResult repositoryScanResult) throws Exception
	{
		// Process files first
		File root = new File(repositoryPath);
		String[] files = (root.list() != null ? root.list() : new String[0]); 
		for(String fileName : files)
		{
			File file = new File(root, fileName);
			// Skip directories for now, see below
			if(file.isDirectory())
				continue;    		
			// Could this be a Salesforce file?
			int extensionPosition = file.getName().lastIndexOf(".");
			if(extensionPosition == -1) // File extension?
				continue;
			String fileExtension = file.getName().substring(extensionPosition+1);
			String fileNameWithoutExtension = file.getName().substring(0, extensionPosition); 
			// Could this be Salesforce metadata file?
			if(fileExtension.equals("xml"))
			{
				// Adjust to look for a Salesforce metadata file extension?
				extensionPosition = fileNameWithoutExtension.lastIndexOf(".");
				if(extensionPosition != -1)
					fileExtension = file.getName().substring(extensionPosition + 1);
			}    
			// Is this file extension recognised by Salesforce Metadata API?
			File repository = new File(this.getRepositoryPath());
			
			// Remove the repository path and replace windows delimiters
			String[] folders = file.getPath().replace(repository.getPath(),"").replace("\\", "/").split("/");
						
			DescribeMetadataObject metadataObject;
			
			// If we are in the sub folder of a folder, get the parent folder
			if (folders.length > 3) {				
				metadataObject = repositoryScanResult.metadataFolderBySuffix.get(folders[folders.length-3] + ":" + fileExtension);
			// If we are just in the folder get the folder name
			} else if (folders.length > 2) {
				metadataObject = repositoryScanResult.metadataFolderBySuffix.get(folders[folders.length-2] + ":" + fileExtension);
			} else {
				metadataObject = null;
			}
			
			if(metadataObject==null)
			{
				// Is this a Document file which supports any file extension?				
				// A document file within a sub-directory of the 'documents' folder?
				if(folders.length>3 && folders[folders.length-3].equals("documents"))
				{
					// Metadata describe for Document
					metadataObject = repositoryScanResult.metadataFolderBySuffix.get(folders[folders.length-3] + ":null");	
				}
				// A file within the root of the 'document' folder?
				else if(folders.length>2 && folders[folders.length-2].equals("documents"))
				{
					// There is no DescribeMetadataObject for Folders metadata types, emulate one to represent a "documents" Folder
					metadataObject = new DescribeMetadataObject();
					metadataObject.setDirectoryName("documents");
					metadataObject.setInFolder(false);
					metadataObject.setXmlName("Document");
					metadataObject.setMetaFile(true);
					metadataObject.setSuffix("dir");
				}
				else
					continue;
			}
			// Add file    		
			RepositoryItem repositoryItem = new RepositoryItem();
			repositoryItem.file = file;
			repositoryItem.metadataFolder = metadataObject.getDirectoryName();
			repositoryItem.metadataType = metadataObject.getXmlName();
			repositoryItem.metadataFile = metadataObject.getMetaFile();
			repositoryItem.metadataInFolder = metadataObject.getInFolder();
			repositoryItem.metadataSuffix = metadataObject.getSuffix();
			repositoryContainer.repositoryItems.add(repositoryItem);
		}    		
		// Process directories
		for(String fileName : files)
		{
			File file = new File(root, fileName);

			if(file.isDirectory())
			{
				RepositoryItem repositoryItem = new RepositoryItem();
				repositoryItem.file = file;
				repositoryItem.repositoryItems = new ArrayList<RepositoryItem>();

				scanRepository(file.getPath(), repositoryItem, repositoryScanResult);

				if(repositoryItem.repositoryItems.size()>0)
					repositoryContainer.repositoryItems.add(repositoryItem);    			
			}
		}
	}    

	/**
	 * Container to reflect repository structure
	 */
	public static class RepositoryItem
	{
		public File file;    	    	    	
		public ArrayList<RepositoryItem> repositoryItems;
		public String metadataFolder;
		public String metadataType;
		public Boolean metadataFile;
		public Boolean metadataInFolder;
		public String metadataSuffix;

		public String getName(){
			return (file == null ? "" : file.getName());
		}

		public String getPath(){
			return (file == null ? "" : file.getPath().replace("\\", "/"));
		}

		public boolean isDirectory(){
			return (file == null ? false : file.isDirectory());						
		}
	}

	public static class RepositoryScanResult
	{
		//public String packageRepoPath;
		//public RepositoryItem pacakgeRepoDirectory;
		public HashMap<String, DescribeMetadataObject> metadataFolderBySuffix;
	}

}
