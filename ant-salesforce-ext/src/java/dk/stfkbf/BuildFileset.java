package dk.stfkbf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import com.sforce.soap.metadata.DescribeMetadataObject;
import com.sforce.soap.metadata.DescribeMetadataResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectorConfig;

import dk.stfkbf.GeneratePackageFileset.RepositoryItem;
import dk.stfkbf.GeneratePackageFileset.RepositoryScanResult;

public class BuildFileset extends Task {

	private String destPath;
	private String gitRoot;
	private String commit1;
	private String commit2;

	private String username;
	private String password;
	private String authEndpoint;

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
	
	public String getDestPath() {
		return destPath;
	}

	public void setDestPath(String destPath) {
		this.destPath = destPath;
	}

	public String getGitRoot() {
		return gitRoot;
	}

	public void setGitRoot(String gitRoot) {
		this.gitRoot = gitRoot;
	}

	public String getCommit1() {
		return commit1;
	}

	public void setCommit1(String commit1) {
		this.commit1 = commit1;
	}

	public String getCommit2() {
		return commit2;
	}

	public void setCommit2(String commit2) {
		this.commit2 = commit2;
	}

	private HashMap<String, Integer> flowMap = new HashMap<String, Integer>();

	public static void main(String[] args){
		BuildFileset fileset = new BuildFileset();

		fileset.setUsername(args[0]);
		fileset.setPassword(args[1]);
		fileset.setAuthEndpoint(args[2]);
		
		fileset.setDestPath(args[3]);
		fileset.setGitRoot(args[4]);
		fileset.setCommit1(args[5]);
		fileset.setCommit2(args[6]);

		fileset.execute();
	}

	public void execute()  {
		try {
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
				repositoryScanResult.metadataFolderBySuffix.put(describeObject.getSuffix(), describeObject);
				if(describeObject.getMetaFile())
					repositoryScanResult.metadataFolderBySuffix.put(describeObject.getSuffix() + "-meta.xml", describeObject);
			}
			
			
			Pattern pattern = Pattern.compile("(.*)-(\\d+)\\.(.*)");

			Git git = Git.open(new File(this.getGitRoot()));
			Repository repository = git.getRepository();
			ObjectId headId = repository.resolve(this.getCommit1());
			ObjectId oldId = repository.resolve(this.getCommit2());

			ObjectReader reader = repository.newObjectReader();

			CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
			oldTreeIter.reset(reader, oldId);

			CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(reader, headId);

			List<DiffEntry> diffs= git.diff().setShowNameAndStatusOnly(true)
					.setNewTree(newTreeIter)
					.setOldTree(oldTreeIter)
					.call();
			
			for (DiffEntry diff : diffs){
				System.out.println(diff.toString());

				if (!diff.getChangeType().equals(ChangeType.DELETE)){
					File temp = new File(diff.getNewPath());
					File source = new File(gitRoot, diff.getNewPath());
					File sourceMeta = new File(gitRoot, diff.getNewPath() + "-meta.xml");

					int extensionPosition = source.getName().lastIndexOf(".");
					if(extensionPosition == -1) // File extension?
						continue;
					String fileExtension = source.getName().substring(extensionPosition+1);
					String fileNameWithoutExtension = source.getName().substring(0, extensionPosition); 
					// Could this be Salesforce metadata file?
					if(fileExtension.equals("xml"))
					{
						// Adjust to look for a Salesforce metadata file extension?
						extensionPosition = fileNameWithoutExtension.lastIndexOf(".");
						if(extensionPosition != -1)
							fileExtension = source.getName().substring(extensionPosition + 1);
					}    
					// Is this file extension recognised by Salesforce Metadata API?
					DescribeMetadataObject metadataObject = repositoryScanResult.metadataFolderBySuffix.get(fileExtension);
					
					if (metadataObject == null)
						continue;
					
					Matcher matcher = pattern.matcher(source.getName());
					Integer maxVersion = 0;

					// Check if we are dealing with a flow
					if (matcher.matches() && matcher.group(3).equals("flow")){
						// Check if we have dealt with this flow before
						if (!flowMap.containsKey(matcher.group(1))){
							// Get the version of the flow we are looking at
							Integer currentVersion = Integer.decode(matcher.group(2));

							// Find the maximum version (all versions should be in the source directory)
							DirectoryScanner scanner = new DirectoryScanner();
							scanner.setIncludes(new String[]{"**/" + matcher.group(1) + "*.flow"});
							scanner.setBasedir(this.getGitRoot());
							scanner.setCaseSensitive(false);
							scanner.scan();
							String[] files = scanner.getIncludedFiles();
							for (String flowFile : files){								
								Matcher flowMatcher = pattern.matcher(flowFile);
								if (flowMatcher.matches() && flowMatcher.groupCount() == 3)
									maxVersion = (Integer.decode(flowMatcher.group(2)) > currentVersion ? Integer.decode(flowMatcher.group(2)) : currentVersion); 

							}
							if (maxVersion == currentVersion){
								flowMap.put(matcher.group(1), maxVersion);

								// Create a new version
								File target = null;
							
								if (diff.getChangeType().equals(ChangeType.ADD)){
									target = new File(this.getDestPath(), temp.getPath().replace(source.getName(), "") +  matcher.group(1) + "-" + (maxVersion) + ".flow");
								} else {
									target = new File(this.getDestPath(), temp.getPath().replace(source.getName(), "") +  matcher.group(1) + "-" + (maxVersion + 1) + ".flow");
								}
							
								target.getParentFile().mkdirs();

								copyFile(source, target);
							}
						}
					} else {
						File target = new File(this.getDestPath(), temp.getPath());
						File targetMeta = new File(this.getDestPath(), temp.getPath() + "-meta.xml");
						target.getParentFile().mkdirs();

						if (temp.getPath().endsWith("profile")){
							// Remove SocialPersona tab
							String file = FileUtils.readFileToString(source);
						    Pattern social = Pattern.compile("((?s).*)(<tabVisibilities>(?s).*<tab>standard-SocialPersona</tab>(?s).*<visibility>DefaultOff</visibility>(?s).*</tabVisibilities>)((?s).*)");
						    Matcher socialMatcher = social.matcher(file);
						    if (socialMatcher.matches()){
						    	file = file.replace(socialMatcher.group(2), "");
						    }
						    FileUtils.write(target, file);
						} else if (temp.getPath().endsWith("assignmentRules") || temp.getPath().endsWith("escalationRules") || temp.getPath().endsWith("settings")) {
							// Modify User
							String file = FileUtils.readFileToString(source);
						    Pattern user = Pattern.compile("((?s).*)(stfkbf-env1@gmail.com)((?s).*)");
						    Matcher userMatcher = user.matcher(file);
						    if (userMatcher.matches()){
						    	file = file.replace(userMatcher.group(2), "stfkbf-env2@gmail.com");
						    }
						    FileUtils.write(target, file);
						}else {
							copyFile(source, target);
							
							if (sourceMeta.exists()){
								copyFile(sourceMeta, targetMeta);
							}
						}
						
						
					}
				}
			}
		} catch (Exception e){
			e.printStackTrace();
		}

	}

	private void copyFile(File source, File target)
			throws FileNotFoundException, IOException {
		InputStream is = null;
		OutputStream os = null;
		try {
			is = new FileInputStream(source);
			os = new FileOutputStream(target);
			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) > 0) {
				os.write(buffer, 0, length);
			}
		} finally {
			is.close();
			os.close();
		}
	}
}
