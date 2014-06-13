package dk.stfkbf;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;

public class PrepareFileset extends Task {

	private String filesetPath;	
	private String targetPath;
	private String properties;
	private String fixVersions = "true";

	public HashMap<String, HashMap<String, HashMap<String, String>>> configuration = 
			new HashMap<String, HashMap<String, HashMap<String, String>>>(); 


	private HashMap<String, Integer> flowMap = new HashMap<String, Integer>();
	private Pattern pattern = Pattern.compile("(.*)-(\\d+)\\.(.*)");

	public String getFilesetPath() {
		return filesetPath;
	}

	public void setFilesetPath(String filesetPath) {
		this.filesetPath = filesetPath;
	}

	public String getTargetPath() {
		return targetPath;
	}

	public void setTargetPath(String targetPath) {
		this.targetPath = targetPath;
	}	

	public String getFixVersions() {
		return fixVersions;
	}

	public void setFixVersions(String fixVersions) {
		this.fixVersions = fixVersions;
	}

	public String getProperties() {
		return properties;
	}

	public void setProperties(String properties) {
		this.properties = properties;
	}

	public static void main(String[] args){
		PrepareFileset fileset = new PrepareFileset();

		fileset.setFilesetPath("D:/git/output/upsert");
		fileset.setTargetPath("D:/git/temp");
		fileset.setProperties("D:/git/ant/prepareFileset.properties");

		fileset.execute();
	}

	public void execute()  {
		try {
			loadConfiguration();

			// Loop through all files
			File target = new File(this.getFilesetPath());

			for (String folderName : configuration.keySet()){
				File folder = new File(target + "/" + folderName);
				System.out.println(target + "/" + folderName);
				if (!folder.exists())
					continue;

				for (File file : folder.listFiles()){
					if (!file.isDirectory()){
						String content = FileUtils.readFileToString(file);

						for (String entry : configuration.get(folderName).keySet()){
							String expression = configuration.get(folderName).get(entry).get("expression");							
							Pattern pattern = Pattern.compile(expression, Pattern.DOTALL);

							boolean checkMissing = false;
							String expressionMissing = "";

							if (configuration.get(folderName).get(entry).containsKey("missing")){
								checkMissing = true;
								expressionMissing = configuration.get(folderName).get(entry).get("missing");
							}

							String original = content;

							Matcher matcher = pattern.matcher(original);
							while (matcher.find()){								
								int group = Integer.parseInt(configuration.get(folderName).get(entry).get("group"));

								String match = matcher.group(group);
								Pattern missingPattern = Pattern.compile(expressionMissing, Pattern.DOTALL);
								Matcher missingMatcher = missingPattern.matcher(match);

								if(!checkMissing || !missingMatcher.matches()){
									String replacement = configuration.get(folderName).get(entry).get("replacement");

									content = content.replace(match, replacement);

									System.out.println(file.getName() + " " + folderName + "." + entry + " matched");
								} else {
									System.out.println(file.getName() + " " + folderName + "." + entry + " not matched (secondary)");

								}
							}
						}

						FileUtils.write(file, content);
					}
				}
			}

			if (fixVersions.equals("true")){
				// Fix flows
				File filesetFolder = new File(this.getFilesetPath() + "/flows");
				File targetFolder = new File(this.getTargetPath() + "/flows");

				Integer maxVersion = 0;
				Integer targetMaxVersion = 0;
				if (filesetFolder.exists() && targetFolder.exists()){
					for (File flow : filesetFolder.listFiles()){
						Matcher matcher = pattern.matcher(flow.getName());
						if (matcher.matches() && !flowMap.containsKey(matcher.group(1))){
							Integer currentVersion = Integer.decode(matcher.group(2));

							maxVersion = findMaxVersion(filesetFolder.getAbsolutePath(), matcher.group(1), currentVersion);

							if (maxVersion == currentVersion){
								targetMaxVersion = findMaxVersion(targetFolder.getAbsolutePath(), matcher.group(1), currentVersion);
								flowMap.put(matcher.group(1), targetMaxVersion + 1);						
								// Rename the file

								File newFlow = new File(this.getFilesetPath() + "/flows/" + matcher.group(1) + "-" + (targetMaxVersion + 1) + ".flow");
								FileUtils.moveFile(flow, newFlow);
							} else {
								// Delete the file
								flow.delete();
							}
						}				
					}
				}
			}
			// Fix milestones
			// File milestoneFolder = new File(target + "/" + )
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private Integer findMaxVersion(String folderName, String flowName, Integer currentVersion){
		Integer maxVersion = 0;

		// Find the maximum version (all versions should be in the source directory)
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setIncludes(new String[]{"**/" + flowName + "*.flow"});
		scanner.setBasedir(folderName);
		scanner.setCaseSensitive(false);
		scanner.scan();
		String[] files = scanner.getIncludedFiles();
		for (String flowFile : files){								
			Matcher flowMatcher = pattern.matcher(flowFile);
			if (flowMatcher.matches() && flowMatcher.groupCount() == 3)
				maxVersion = (Integer.decode(flowMatcher.group(2)) > currentVersion ? Integer.decode(flowMatcher.group(2)) : currentVersion); 
		}

		return maxVersion;
	}

	private void loadConfiguration() throws Exception{
		Properties properties = new Properties();

		properties.load(new FileInputStream(this.getProperties()));

		for (String key : properties.stringPropertyNames()){
			String[] property = key.split("\\.");

			if (!configuration.containsKey(property[0]))
				configuration.put(property[0], new HashMap<String, HashMap<String, String>>());

			if (!configuration.get(property[0]).containsKey(property[1]))
				configuration.get(property[0]).put(property[1], new HashMap<String, String>());

			configuration.get(property[0]).get(property[1]).put(property[2], properties.getProperty(key, ""));
		}
	}
}
