package dk.stfkbf;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;

import com.sforce.ws.bind.XMLizable;

public class ObjDiffNode extends DiffNode {
	private Set<AttrDiffNode> attrDiffs = new TreeSet<AttrDiffNode>();

	public Set<AttrDiffNode> getAttrDiffs() {
		return attrDiffs;
	}


	public void setAttrDiffs(Set<AttrDiffNode> attrDiffs) {
		this.attrDiffs = attrDiffs;
	}

	private String metadataClassName;
	private String fileName;
	
	public ObjDiffNode(String name) {
		super(name);
	}
	

	public String getMetadataClassName() {
		return metadataClassName;
	}


	public void setMetadataClassName(String metadataClassName) {
		this.metadataClassName = metadataClassName;
	}


	public String getFileName() {
		return fileName;
	}


	public void setFileName(String fileName) {
		this.fileName = fileName;
	}


	public void addAttrDiff(AttrDiffNode attrDiff) {
		attrDiffs.add(attrDiff);
	}

	@Override
	public void removeNoDiffNodes() {
		Set<AttrDiffNode> noDiffAttrNodes = new TreeSet<AttrDiffNode>();
		for (AttrDiffNode attrDifNode : attrDiffs) {
			if (attrDifNode.isNoDiff())
				noDiffAttrNodes.add(attrDifNode);
		}
		attrDiffs.removeAll(noDiffAttrNodes);
		super.removeNoDiffNodes();

	}
	
	public StringBuilder diffPrint(String indent) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		StringBuilder stringBuilder = new StringBuilder();
		if (attrDiffs.size() > 0) {
			stringBuilder.append(indent + "  ").append("Attributes\n");
			for (AttrDiffNode attrDiffNode : attrDiffs)
				stringBuilder.append(indent + "    ").append(attrDiffNode.toString()).append('\n');
		}
		return stringBuilder;
	}

	@Override
	public boolean isNoDiff() {
		return attrDiffs.size() == 0 && subDirDiffNodes.size() == 0 && objDiffs.size() == 0;
	}

	static public class AttrDiffNode implements Comparable<AttrDiffNode> {
		private String name;
		private Object sourceValue;
		private Object targetValue;
		
		public String toString() {
			return name + ": " + valueToString(sourceValue) + ", " + valueToString(targetValue);
		}
		
		public AttrDiffNode(String name) {
			this.name = name;
		}
		
		private String valueToString(Object object) {
			if (object == null)
				return null;
			
			if (object instanceof XMLizable) {
				String type = object.getClass().getSimpleName() + getNextUnNamedFilePostfix();
				try {
					FileUtils.write(new File(getOutputRootDir() + "/XMLizable/" + type), object.toString());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return type;
			}
			return DiffNode.getValueForPrint(object).toString();
		}
		public boolean isNoDiff() {
			if (sourceValue == null && targetValue == null)
				return true;
			
			if (sourceValue == null || targetValue == null)
				return false;
			
			return sourceValue.equals(targetValue);
		}

		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public Object getSourceValue() {
			return sourceValue;
		}
		public void setSourceValue(Object sourceValue) {
			this.sourceValue = sourceValue;
		}
		public Object getTargetValue() {
			return targetValue;
		}
		public void setTargetValue(Object targetValue) {
			this.targetValue = targetValue;
		}
		@Override
		public int compareTo(AttrDiffNode o) {
			return name.compareTo(o.getName());
		}
	
	}

}