package dk.stfkbf;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.Task;

import dk.stfkbf.ObjDiffNode.AttrDiffNode;
import com.sforce.soap.metadata.Metadata;
import com.sforce.ws.bind.TypeMapper;
import com.sforce.ws.bind.XMLizable;
import com.sforce.ws.parser.XmlInputStream;

/*
 * This class is from the Salesforce Build Tool Set. 
 * 
 * The original author unfortunately is not known.
 * 
 */
public class ForceDiff extends Task{
	private static final String CONTENT_FILENAME_PATTERN = "-meta.xml";
	private static final Pattern XML_PATTERN = Pattern.compile("<?xml.*?>");
	private static final Pattern ATTRIIBUTE_PATTERN = Pattern.compile("^get|^is");

	private String sourceDir;
	private String targetDir;
	private String outputDir;
	
	private Pattern filterPattern = Pattern.compile("package.xml|DS_Store|.svn");
	private Pattern contentPattern = Pattern.compile("\\.resource$|\\.cls$|\\.page$|\\.trigger$|\\.component$|\\.email");
	private DirDiffNode topDiffNode = new DirDiffNode("root");
	private TreeSet<String> typeSet;

	public static final TypeMapper typeMapper = new TypeMapper();

	public void setSourceDir(String sourceDir) {
		this.sourceDir = sourceDir.replace('\\', '/');
	}

	public void setTargetDir(String targetDir) {
		this.targetDir = targetDir.replace('\\', '/');
	}

	public void setOutputDir(String outputDir) {
		this.outputDir = outputDir.replace('\\', '/');
	}

	public void setMetadataTypes(String metadataTypes) {
		if (metadataTypes!=null && !metadataTypes.trim().equals("") && !metadataTypes.equalsIgnoreCase("all"))
			typeSet = new TreeSet<String>(Arrays.asList(metadataTypes.split(",")));
		System.out.println("typeSet="+typeSet);
	}

	public static void main(String[] argv) {
		ForceDiff diff = new ForceDiff();
		diff.setSourceDir("/Users/blixen/git/dev-env/project/src");
		diff.setTargetDir("/Users/blixen/Developer/salesforce_ant_29/temp");
		diff.setMetadataTypes("all");
		diff.setOutputDir("/Users/blixen/Developer/salesforce_ant_29/output");
		diff.execute();
	}

	private FileFilter fileFilter = new FileFilter() {
		public boolean accept(File file) {
	        return !filterPattern.matcher(file.getName()).find() && 
	        		!contentPattern.matcher(file.getName()).find();
		}
	};
	

	public static void initMapping() throws IOException {
		Properties  prop = new Properties();
		prop.load(ForceDiff.class.getClassLoader().getResourceAsStream("ClassToGetterMapping.properties"));
		Map<String, List<String>> classToGetterMap = new HashMap<String, List<String>>();
		for (Enumeration e = prop.propertyNames(); e.hasMoreElements();) {
			String name = (String)e.nextElement();
			String value = prop.getProperty(name);
			classToGetterMap.put(name, Arrays.asList(value.split(",")));
		}
		DiffNode.setClassToGetterMap(classToGetterMap);
		
	}
	
	public void execute() {
		try {
			initMapping();
			compare();
			generateFiles();
		} catch (Throwable  e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
	}
	
	
	public void generateFiles() throws Exception {
		Map<String, Set<String>> destrctMap = new TreeMap<String, Set<String>>();
		Map<String, Set<String>> upsertMap = new TreeMap<String, Set<String>>();
		
		StringBuffer fieldTypeChanges = new StringBuffer();
		for (DiffNode node : getTopDiffNode().getSubDirDiffNodes()) {
			DirDiffNode dirNode = (DirDiffNode)node;
			if (dirNode.getSourceOnly() != null) {
				for (Object source: dirNode.getSourceOnly()) {
					File file = new File(sourceDir + "/" + dirNode.getName() + "/" + source);
					if (file.isDirectory()) {
						for (File f : file.listFiles()) {
							String metadataType = readMetadataType(f);
							if (metadataType == null) continue;
							addToMapSet(metadataType, source + "/" + getObjectNameFromFileName(f.getName()), upsertMap);							
						}
					}
					else {
						String metadataType = readMetadataType(file);
						if (metadataType == null) continue;
						addToMapSet(metadataType, getObjectNameFromFileName((String)source), upsertMap);
					}
				}
			}
			if (dirNode.getTargetOnly() != null) {
				for (Object target: dirNode.getTargetOnly()) {
					File file = new File(targetDir + "/" + dirNode.getName() + "/" + target);
					if (file.isDirectory()) {
						for (File f : file.listFiles()) {
							String metadataType = readMetadataType(f);
							if (metadataType != null)
								addToMapSet(metadataType, target + "/" +getObjectNameFromFileName(f.getName()), destrctMap);							
						}
					}
					else {
						String metadataType = readMetadataType(file);
						if (metadataType == null) continue;
						if (node.getName().equals("objects") || node.getName().equals("workflows")) {
							if (getObjectNameFromFileName((String)target).endsWith("__c"))
									addToMapSet(metadataType, getObjectNameFromFileName((String)target), destrctMap);
						}
						else
							addToMapSet(metadataType, getObjectNameFromFileName((String)target), destrctMap);
					}
				}
			}
			for (DiffNode objNode : node.getObjDiffs()) 
					addToMapSet(((ObjDiffNode)objNode).getMetadataClassName(), getObjectNameFromFileName(((ObjDiffNode)objNode).getFileName()), upsertMap);
			
			for (DiffNode dNode : node.getSubDirDiffNodes())
			{
				DirDiffNode subDirNode = (DirDiffNode)dNode;
				if (subDirNode.getSourceOnly() != null) {
					for (Object source: subDirNode.getSourceOnly()) {
						File file = new File(sourceDir + "/" + dirNode.getName() + "/" + dNode.getName() + "/" + source);
						String metadataType = readMetadataType(file);
						if (metadataType != null)
							addToMapSet(metadataType, subDirNode.getName() + "/" + getObjectNameFromFileName((String)source), upsertMap);
					}
				}
				if (subDirNode.getTargetOnly() != null) {
					for (Object target: subDirNode.getTargetOnly()) {
						File file = new File(targetDir + "/" + dirNode.getName() + "/" + dNode.getName() + "/" + target);
						String metadataType = readMetadataType(file);
						if (metadataType != null) {
							addToMapSet(metadataType, subDirNode.getName() + "/" + getObjectNameFromFileName((String)target), destrctMap);
						}
					}
				}
				for (DiffNode objNode : subDirNode.getObjDiffs()) {
					addToMapSet(((ObjDiffNode)objNode).getMetadataClassName(), subDirNode.getName() + "/" + getObjectNameFromFileName(((ObjDiffNode)objNode).getFileName()), upsertMap);
				}
			}
			
			if (node.getName().equals("objects")) {
				for (DiffNode objNode : node.getObjDiffs()) {
					String objName = objNode.getName().substring(0, objNode.getName().indexOf(".object"));
					for (DiffNode subDirNode : objNode.subDirDiffNodes) {
						DirDiffNode dNode = (DirDiffNode)subDirNode;
						if (subDirNode.getName().equals("Fields")) {
							DirDiffNode fieldsNode = (DirDiffNode)subDirNode;
							for (Object diff: fieldsNode.getObjDiffs()) {
								ObjDiffNode fieldNode = (ObjDiffNode)diff;
								for (AttrDiffNode attrDiff : fieldNode.getAttrDiffs()) {
									addToMapSet("com.sforce.soap.metadata.CustomField", objName + "." + fieldNode.getName(), upsertMap);
									if (attrDiff.getName().equals("Type")) {
										fieldTypeChanges.append(objName + "." + fieldNode.getName() + "   " + attrDiff.getSourceValue() + " --> " + attrDiff.getTargetValue()+"\n");
									}
								}
							}
							for (Object target: fieldsNode.getSourceOnly()) {
								if (target.toString().endsWith("__c"))
									addToMapSet("com.sforce.soap.metadata.CustomField", objName + "." + DiffNode.getValueForPrint(target), upsertMap);
							}
							for (Object target: fieldsNode.getTargetOnly()) {
								if (target.toString().endsWith("__c"))
									addToMapSet("com.sforce.soap.metadata.CustomField", objName + "." + DiffNode.getValueForPrint(target), destrctMap);
							}
						}
					}
				}
			}
		}

		FileUtils.forceMkdir(new File(outputDir));
		FileUtils.forceMkdir(new File(outputDir + "/delete/"));
		FileUtils.forceMkdir(new File(outputDir + "/upsert/"));
		FileUtils.write(new File(outputDir + "/delete/destructiveChanges.xml"), mapSetToXML(destrctMap));
		StringBuffer buf = new StringBuffer();
		buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
		buf.append("<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">\n");
		buf.append("\t<version>30</version>\n");
		buf.append("</Package>\n");
		FileUtils.write(new File(outputDir + "/delete/package.xml"), buf);
		FileUtils.write(new File(outputDir + "/upsert/package.xml"), mapSetToXML(upsertMap));
		if (fieldTypeChanges.length() > 0) {
			System.out.println("\n\nYou have field type changes. Please review.");
			System.out.println("Type changes invovling Picklist, Lookup, Formula, Master-Detail might ");
			System.out.println("required you to remove fields manually on your target org before you deploy related objects.");
			FileUtils.write(new File(outputDir + "/fieldChanges"), fieldTypeChanges.toString());
		}
	}
	
	private String mapSetToXML(Map<String, Set<String>> map) throws Exception {
		StringBuffer buf = new StringBuffer();
		buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
		buf.append("<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">\n");
		
		for (String key: map.keySet()) {
			buf.append("\t<types>\n");
			for (String obj : map.get(key))
				buf.append("\t\t<members>" + obj + "</members>\n");
			buf.append("\t\t<name>" + key + "</name>\n");
			buf.append("\t</types>\n");
		}
		buf.append("\t<version>30</version>\n");
		buf.append("</Package>\n");
		return buf.toString();
	}
	
	private String getObjectNameFromFileName(String fileName) {
		String name ;
		name = fileName.substring(0, fileName.indexOf("."));
		if (name.indexOf("-meta") > 0)
			name = name.substring(0, name.indexOf("-meta"));
		return name;
	}
	
	private void addToMapSet(String className, String name, Map<String, Set<String>> map) {
		String type = className.substring(className.lastIndexOf(".")+1);
		if (type.endsWith("Settings"))
			type = "Settings";
		if (type.endsWith("Folder")) 
			type = type.substring(0, type.indexOf("Folder"));
		
		Set<String> set = map.get(type);
		if (set == null) {
			set = new TreeSet<String>();
			map.put(type,  set);
		}
		set.add(name);
	}
	
	public void compare() throws IOException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		topDiffNode.setOutputRootDir(outputDir);
		compareDirectory(new File(sourceDir), new File(targetDir), topDiffNode, "  ");
		topDiffNode.removeNoDiffNodes();
		FileUtils.writeStringToFile(new File(topDiffNode.getOutputRootDir() + "/forcediff"), topDiffNode.print("  ").toString());
	}
	
	private void compareDirectory(File srcDir, File targetDir, DirDiffNode diffNode, String indent) throws IOException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		String dirName = (srcDir == null) ? targetDir.getName() : srcDir.getName();
		System.out.println(indent + "  " + dirName + " <--> " + dirName);
		TreeMap<String, File> sourceMap = getFileMap(srcDir);
		TreeMap<String, File> targetMap = getFileMap(targetDir);
		
		TreeSet<String> sourceOnly = new TreeSet<String>(sourceMap.keySet());
		TreeSet<String> targetOnly = new TreeSet<String>(targetMap.keySet());
	
		if (diffNode == topDiffNode && typeSet != null && typeSet.size() != 0) {
			sourceOnly.retainAll(typeSet);
			targetOnly.retainAll(typeSet);
		}
		TreeSet<String> commonSet = (TreeSet<String>)sourceOnly.clone();
		commonSet.retainAll(targetOnly);
		sourceOnly.removeAll(commonSet);
		targetOnly.removeAll(commonSet);
		
		if (sourceOnly.size() > 0) {
			diffNode.setSourceOnly(sourceOnly);
			for (String sourceOnlyFileName : sourceOnly) {
				File file = sourceMap.get(sourceOnlyFileName);
				if (file.isFile())
					copyMetadataFileToUpsert(file.getAbsolutePath());
				else {
					File sourceDirFile = new File(sourceDir);
					File outputDirFile = new File(outputDir  + "/upsert/");
					String destDir = file.getAbsolutePath().replace(sourceDirFile.getAbsolutePath(), outputDirFile.getAbsolutePath());
					FileUtils.copyDirectory(file, new File(destDir));
				}
			}
		}
		
		if (targetOnly.size() > 0) {
			diffNode.setTargetOnly(targetOnly);
		}
		
		Set<String> dirList = new TreeSet<String>();
		for (String filename: sourceOnly) {
			File file = sourceMap.get(filename);
			if (file.isDirectory()) {
				dirList.add(filename);
				DirDiffNode dirDiffNode = new DirDiffNode(file.getName());
				diffNode.addSubDirDiffNode(dirDiffNode);
				compareDirectory(file, null, dirDiffNode, indent + "  ");
			}
		}
		sourceOnly.removeAll(dirList);
		
		dirList = new TreeSet<String>();
		for (String filename: targetOnly) {
			File file = targetMap.get(filename);
			if (file.isDirectory()) {
				dirList.add(filename);
				DirDiffNode dirDiffNode = new DirDiffNode(file.getName());
				diffNode.addSubDirDiffNode(dirDiffNode);
				compareDirectory(null, file, dirDiffNode, indent + "  ");
			}
		}
		sourceOnly.removeAll(dirList);
		
		for (String filename : commonSet) {
			File sourceFile = sourceMap.get(filename);
			File targetFile = targetMap.get(filename);
			if (sourceFile.isDirectory() && targetFile.isDirectory()) {
				DirDiffNode dirDiffNode = new DirDiffNode(sourceFile.getName());
				diffNode.addSubDirDiffNode(dirDiffNode);
				compareDirectory(sourceFile, targetFile, dirDiffNode, indent + "  ");
			}
			else if (sourceFile.isFile() && targetFile.isFile()) {
				ObjDiffNode objDiff = new ObjDiffNode(sourceFile.getName());
				compareFile(sourceFile, targetFile, objDiff, indent + "  ");	
				diffNode.addObjDiffs(objDiff);
			}
			else {
				System.out.println("Error: " + sourceFile + " and " + targetFile + " has the same name, but one is file and the other is directory");
			}
			diffNode.removeNoDiffNodes();
		}
	}
	
	private boolean isTextFile(String filePath) throws IOException { 
		File f = new File(filePath);
	    if(!f.exists())
	        return false;
	    FileInputStream in = new FileInputStream(f);
	    int size = in.available();
	    if(size > 1000)
	        size = 1000;
	    byte[] data = new byte[size];
	    in.read(data);
	    in.close();
	    String s = new String(data, "ISO-8859-1");
	    String s2 = s.replaceAll(
	            "[a-zA-Z0-9ßöäü\\.\\*!\"§\\$\\%&/()=\\?@~'#:,;\\"+
	            "+><\\|\\[\\]\\{\\}\\^°²³\\\\ \\n\\r\\t_\\-`´âêîô"+
	            "ÂÊÔÎáéíóàèìòÁÉÍÓÀÈÌÒ©‰¢£¥€±¿»«¼½¾™ª]", "");
	    // will delete all text signs

	    double d = (double)(s.length() - s2.length()) / (double)(s.length());
	    // percentage of text signs in the text
	    return d > 0.95;
	}
	
	private String readTextFile(String fileName) throws IOException {
		StringBuilder builder = new StringBuilder();
		LineNumberReader lnr = new LineNumberReader(new FileReader(fileName));
		String line;
		while ((line = lnr.readLine()) != null) {
			if (!line.trim().equals("")) {
				builder.append(line.trim() +'\n');
			}
		}
		lnr.close();
		return builder.toString();
	}
	
	private boolean isFileContentEqual(String sourceFileName, String targetFileName) throws IOException {
		diff_match_patch diff = new diff_match_patch();
		LinkedList<Diff> d = diff.diff_main(readTextFile(sourceFileName), 
				readTextFile(targetFileName));
		
		Diff dif = d.getFirst();

		return (d.size() == 1 && dif.operation.name().equals("EQUAL"));		
	}
	
	private void copyMetadataFileToUpsert(String sourceFileName) throws IOException {
		String sourceFilePath = sourceFileName.replace('\\', '/');
		String upsertFile = sourceFilePath.replace(sourceDir, outputDir + "/upsert/");
		//System.out.println(sourceDir + " " + sourceFilePath + " " + outputDir + " " + upsertFile);
		FileUtils.copyFile(new File(sourceFileName), new File(upsertFile));
		if  (sourceFileName.endsWith(CONTENT_FILENAME_PATTERN)) {
			File sourceContentFile = new File(sourceFileName.replace(CONTENT_FILENAME_PATTERN, ""));
			if (sourceContentFile.isFile())
				FileUtils.copyFile(sourceContentFile, new File(upsertFile.replace(CONTENT_FILENAME_PATTERN, "")));
		}
	}
	private void compareFile(File sourceFile, File targetFile, ObjDiffNode objDiffNode, String indent) throws IOException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		
		//boolean isMetadataEqual = FileUtils.contentEquals(sourceFile, targetFile);
		boolean isMetadataEqual = isFileContentEqual(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
		boolean isContentEqual = false;
		boolean hasContent = false;
		
		System.out.println(indent + "  " + sourceFile.getName() + " <--> " + targetFile.getName() + " " + isMetadataEqual);
		
		if (sourceFile.getName().endsWith(CONTENT_FILENAME_PATTERN)) {
			String sourceContentFileName = sourceFile.getAbsolutePath().substring(0,  sourceFile.getAbsolutePath().length() - CONTENT_FILENAME_PATTERN.length());
			if (contentPattern.matcher(sourceContentFileName).find()) {
				hasContent = true;
				String targetContentFileName = targetFile.getAbsolutePath().substring(0,  targetFile.getAbsolutePath().length() - CONTENT_FILENAME_PATTERN.length());
				if (isTextFile(sourceContentFileName)) {
					/*
					diff_match_patch diff = new diff_match_patch();
					LinkedList<Diff> d = diff.diff_main(readTextFile(sourceContentFileName), 
							readTextFile(targetContentFileName));
					
					Diff dif = d.getFirst();

					isContentEqual = (d.size() == 1 && dif.operation.name().equals("EQUAL"));
					*/
					isContentEqual = isFileContentEqual(sourceContentFileName, targetContentFileName);
				}
				/*
				if (targetContentFileName.endsWith(".cls") || targetContentFileName.endsWith(".component") ||
						targetContentFileName.endsWith(".page")) {
					String content1 = FileUtils.readFileToString(new File(sourceContentFileName));
					String content2 = FileUtils.readFileToString(new File(targetContentFileName));
					isContentEqual = content1.trim().equals(content2.trim());
				}*/
				else {
					isContentEqual = FileUtils.contentEquals(new File(sourceContentFileName), 
							new File(targetContentFileName));
				}
			}
		}
		
		
		if (isContentEqual && isMetadataEqual)
			return;
		
		if (!hasContent && isMetadataEqual)
			return;
		
		copyMetadataFileToUpsert(sourceFile.getAbsolutePath());

		Metadata sourceData = readMetadata(sourceFile);
		Metadata targetData = readMetadata(targetFile);
		objDiffNode.setFileName(sourceFile.getName());
		objDiffNode.setMetadataClassName(sourceData.getClass().getName());

    if (isMetadataEqual && !isContentEqual) {
			ObjDiffNode.AttrDiffNode attrDiffNode = new ObjDiffNode.AttrDiffNode("Content");
			attrDiffNode.setSourceValue("Content 1");;
			attrDiffNode.setTargetValue("Content 2");
			objDiffNode.addAttrDiff(attrDiffNode);
			return;
		}
		
        compareObject(sourceData, targetData, objDiffNode, indent);
	}

	private void compareObject(XMLizable sourceData, XMLizable targetData, ObjDiffNode objectDifference, String indent) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
		Class metadataClass = sourceData.getClass();
		for (Method method : metadataClass.getMethods()) {
			String methodName = method.getName();
			//System.out.println(methodName);
            if (methodName.startsWith("get") || methodName.startsWith("is")) {
                if (method.getParameterTypes().length == 0 && !methodName.equals("getClass")) {
                    Object sourceGetResult = method.invoke(sourceData);
                    Object targetGetResult = method.invoke(targetData);
                    if (sourceGetResult == null && targetGetResult == null)
                    	continue;
                    if ((sourceGetResult == null || targetGetResult == null)) {
               			addObjectDifference(objectDifference, methodName, sourceGetResult,  targetGetResult);
                     }
                    else {
                    	if (sourceGetResult instanceof XMLizable) {
                    		ObjDiffNode objDiffNode = new ObjDiffNode(getAttributeName(methodName));
                    		objectDifference.addObjDiffs(objDiffNode);
                    		compareObject((XMLizable)sourceGetResult, (XMLizable)targetGetResult, objDiffNode, indent + "  ");
                    	}
                    	else if (sourceGetResult.getClass().isArray()) {
                    		Object[] sourceList = (Object[])sourceGetResult;
                    		Object[] targetList = (Object[])targetGetResult;
                    		if (sourceList.length == 0 && targetList.length == 0)
                    			continue;
                			TreeMap<String, Object> sourceMap = arrayToMap(sourceList);
                   			TreeMap<String, Object> targetMap = arrayToMap(targetList);
                   			TreeSet<String> sourceOnly = new TreeSet<String>(sourceMap.keySet());
                   			TreeSet<String> targetOnly = new TreeSet<String>(targetMap.keySet());
                   			TreeSet<String> commonSet = (TreeSet<String>)sourceOnly.clone();
                   			commonSet.retainAll(targetOnly);
                   			sourceOnly.removeAll(commonSet);
                   			targetOnly.removeAll(commonSet);
                   			
                   			DirDiffNode dirDiffNode = addArrayDifference(objectDifference, methodName, sourceOnly, targetOnly);
                   			
                   			for (String key : commonSet) {
                   				Object sourceObject = sourceMap.get(key);
                   				Object targetObject = targetMap.get(key);
                   				if (sourceObject instanceof XMLizable) {
                   					ObjDiffNode objDiffNode = new ObjDiffNode(key);
                   					dirDiffNode.addObjDiffs(objDiffNode);
                   					compareObject((XMLizable)sourceObject, (XMLizable)targetObject, objDiffNode, indent + " ");
                   					objDiffNode.removeNoDiffNodes();
                   				}
                   			}
                   			dirDiffNode.removeNoDiffNodes();
                    	}
                    	else {
                    		if (!sourceGetResult.equals(targetGetResult)){
                    			addObjectDifference(objectDifference, methodName, sourceGetResult, targetGetResult);
                    		}
                    	}
                    }
                }
            }
		}
		objectDifference.removeNoDiffNodes();
	}
	
	private DirDiffNode addArrayDifference(ObjDiffNode objDiffNode, String methodName, Set sourceOnly, Set targetOnly) {
		DirDiffNode dirDiffNode = new DirDiffNode(getAttributeName(methodName));
		objDiffNode.addSubDirDiffNode(dirDiffNode);
		dirDiffNode.setSourceOnly(sourceOnly);
		dirDiffNode.setTargetOnly(targetOnly);
		return dirDiffNode;
	}
	private void addObjectDifference(ObjDiffNode objectDifference, String methodName, Object sourceValue, Object targetValue) {
		ObjDiffNode.AttrDiffNode attributeDifference = new ObjDiffNode.AttrDiffNode(getAttributeName(methodName));
		attributeDifference.setSourceValue(sourceValue);
		attributeDifference.setTargetValue(targetValue);
		objectDifference.addAttrDiff(attributeDifference);
	}
	private String getAttributeName(String name) {
		Matcher matcher = ATTRIIBUTE_PATTERN.matcher(name);
		if (matcher.find()) {
			return name.substring(matcher.end());
		}
		return name;
	}
	private TreeMap<String, Object> arrayToMap(Object[] objects) throws SecurityException, IllegalArgumentException, NoSuchElementException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		TreeMap<String, Object> map = new TreeMap<String, Object>();
		for (int i = 0; i < objects.length; i++)
			if (objects[i] != null)
				map.put(DiffNode.getValue(objects[i], i), objects[i]);
		return map;
	}
		
	private Metadata readMetadata(File file) throws IOException {
		String content = FileUtils.readFileToString(file).trim();

		try {
			Matcher matcher = XML_PATTERN.matcher(content);
			if (! matcher.find())
				throw new Exception (file.getAbsolutePath() + " is not a xml file");
			
			String metadataType = content.substring(matcher.end()).trim();
			metadataType = metadataType.substring(1, metadataType.indexOf(" "));
			String className = "com.sforce.soap.metadata." + metadataType;
			Class<? extends Metadata>  metadataClass = (Class<? extends Metadata> )Class.forName(className);
			Metadata metadata = metadataClass.newInstance();
			XmlInputStream xmlInputStream = new XmlInputStream();
			//TODO: SB-F: Is this really the way?
			content = content.replace("UTF-8", "UTF_8");			
			xmlInputStream.setInput(IOUtils.toInputStream(content), "UTF-8");
			metadata.load(xmlInputStream, typeMapper);
			return metadata;
		}
		catch (Throwable e) {
			System.out.println("Error reading file: " + file.getAbsolutePath());
			e.printStackTrace();
		}
		return null;
	}
	
	private String readMetadataType(File file) throws IOException {
		String content = FileUtils.readFileToString(file).trim();

		try {
			Matcher matcher = XML_PATTERN.matcher(content);
			if (! matcher.find())
				return null;
			
			String metadataType = content.substring(matcher.end()).trim();
			metadataType = metadataType.substring(1, metadataType.indexOf(" "));
			
			return "com.sforce.soap.metadata." + metadataType;
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}
	public DirDiffNode getTopDiffNode() {
		return topDiffNode;
	}
	
	public TreeMap<String, File> getFileMap(File dir) {
		TreeMap<String, File> fileMap = new TreeMap<String, File>();
		if (dir == null)
			return fileMap;
		
		File[] files = dir.listFiles(fileFilter);
		
		for (File file: files)
			fileMap.put(file.getName(), file);
		return fileMap;
	}
}
