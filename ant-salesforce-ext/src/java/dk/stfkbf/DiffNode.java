package dk.stfkbf;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;

import com.sforce.ws.bind.XMLizable;

/*
 * This class is from the Salesforce Build Tool Set. 
 * 
 * The original author unfortunately is not known.
 * 
 */
public abstract class DiffNode implements Comparable<DiffNode>{
	static Map<String, List<String>> classToGetterMap = new HashMap<String, List<String>>();
	protected String name;
	protected Set<DiffNode> objDiffs = new TreeSet<DiffNode>();
	protected Set<DiffNode> subDirDiffNodes = new TreeSet<DiffNode>();
	
	protected static String outputRootDir;
	public static int UnNamedFileCounter = 0;

	public static Map<String, List<String>> getClassToGetterMap() {
		return classToGetterMap;
	}

	public static void setClassToGetterMap(Map<String, List<String>> classToGetterMap) {
		DiffNode.classToGetterMap = classToGetterMap;
	}
	public Set<DiffNode> getObjDiffs() {
		return objDiffs;
	}
	
	public static String getNextUnNamedFilePostfix() {
		return String.format("%04d", ++UnNamedFileCounter);
	}
	
	public static String getOutputRootDir() {
		return outputRootDir;
	}

	public static void setOutputRootDir(String outputRootDir) {
		DiffNode.outputRootDir = outputRootDir;
		File file = new File(outputRootDir + "/XMLizable");
		if (!file.exists()) {
			file.mkdirs();
		}
		try {
			FileUtils.cleanDirectory(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

	public DiffNode(String name) {
		this.name = name;
	}

	public abstract boolean isNoDiff(); 
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<DiffNode> getObjectDifferences() {
		return objDiffs;
	}

	public void setObjDiffs(Set<DiffNode> objDiffs) {
		this.objDiffs = objDiffs;
	}

	public Set<DiffNode> getSubDirDiffNodes() {
		return subDirDiffNodes;
	}

	public void addSubDirDiffNode(DirDiffNode dirDiffNode) {
		subDirDiffNodes.add(dirDiffNode);
	}

	public void addObjDiffs(ObjDiffNode objDiff) {
		objDiffs.add(objDiff);
	}
	
	public void setSubDirDiffNodes(Set<DiffNode> subDirDiffNodes) {
		this.subDirDiffNodes = subDirDiffNodes;
	}

	@Override
	public int compareTo(DiffNode o) {
		return name.compareTo(o.getName());
	}

	public abstract StringBuilder diffPrint(String indent) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException;
	
	public StringBuilder print(String indent) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(indent).append(getValueForPrint(name)).append('\n');
		stringBuilder.append(diffPrint(indent));
		for (DiffNode dirDiffNode : subDirDiffNodes)
			stringBuilder.append(dirDiffNode.print(indent + "  "));
		
		for (DiffNode objDiffNode : objDiffs) 
			stringBuilder.append(objDiffNode.print(indent + "  "));
		return stringBuilder;
	}
	public void removeNoDiffNodes() {
		Set<DiffNode> noDiffDirNodes = new TreeSet<DiffNode>();
		for (DiffNode dirDiffNode : subDirDiffNodes) {
			if (dirDiffNode.isNoDiff())
				noDiffDirNodes.add(dirDiffNode);
		}
		subDirDiffNodes.removeAll(noDiffDirNodes);

		Set<DiffNode> noDiffObjNodes = new TreeSet<DiffNode>();
		for (DiffNode objDiffNode : objDiffs) {
			if (objDiffNode.isNoDiff())
				noDiffObjNodes.add(objDiffNode);
		}
		objDiffs.removeAll(noDiffObjNodes);
	}

	public static String getValueForPrint(Object object) {
		return getValueForPrint(object, -1);
	}
	
	public static String getValueForPrint(Object object, int index) {
		if (object == null)
			return null;
		
		
		if (object.toString().startsWith("XMLizable: ")) {
			String content = object.toString().substring("XMLizable: ".length()+1);
			String type = content.substring(0, content.indexOf(" ")) + getNextUnNamedFilePostfix();
			try {
				FileUtils.write(new File(getOutputRootDir() + "/XMLizable/" + type), content);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return type;
		}
		
		
		if (object instanceof XMLizable) {
			List<String> getterMethods = classToGetterMap.get(object.getClass().getName());
			if (getterMethods ==  null) {
				try {
					Method method = object.getClass().getMethod("getFullName");
					Object result = method.invoke(object);
					return result.toString();
				}
				catch (Throwable ee) {
					
				}
				try {
					Method method = object.getClass().getMethod("getName");
					Object result = method.invoke(object);
					return result.toString();
				}
				catch (Throwable ee) {
					if (index > -1) {
						System.out.println("WARNING: Can't get name " + object.getClass().getName() + " use index");
						String type = object.getClass().getName().substring(object.getClass().getName().lastIndexOf('.') + 1);
						return type + " " + index;
					}
					else
					{
						System.out.println("WARNING: Can't get name for " + object.getClass().getName() + " use toString()");
						return "XMLizable";
					}
					
				}
			}
			else {
				StringBuilder returnValue = new StringBuilder();
				for (int i = 0; i < getterMethods.size(); i++) {
					try {
						Method method = object.getClass().getMethod(getterMethods.get(i));
						Object result = method.invoke(object);
						returnValue.append(result == null ? "null" : result.toString());
						if (i != getterMethods.size() - 1)
							returnValue.append(',');
					}
					catch (Throwable e) {
						return "XMLizble";
					}
				}
				return returnValue.toString();
			}
		}
		else
			return object.toString();
	}
	
	public static String getValue(Object object) {
		String value =  getValueForPrint(object);
		if (object == null)
			return null;
		
		if (value.equals("XMLizable"))
			value = "XMLizable: " + object.toString();
		return value;
	}

	public static String getValue(Object object, int index) {
		String value =  getValueForPrint(object, index);
		if (object == null)
			return null;
		
		if (value.equals("XMLizable"))
			value = "XMLizable: " + object.toString();
		return value;
	}
}
