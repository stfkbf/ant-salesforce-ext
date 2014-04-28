package dk.stfkbf;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/*
 * This class is from the Salesforce Build Tool Set. 
 * 
 * The original author unfortunately is not known.
 * 
 */
public class DirDiffNode extends DiffNode {
	private Set sourceOnly;
	private Set targetOnly;
	
	public DirDiffNode(String name) {
		super(name);
	}
	
	public Set getSourceOnly() {
		return sourceOnly;
	}

	public void setSourceOnly(Set sourceOnly) {
		this.sourceOnly = sourceOnly;
	}

	public Set getTargetOnly() {
		return targetOnly;
	}

	public void setTargetOnly(Set targetOnly) {
		this.targetOnly = targetOnly;
	}

	
	public boolean isNoDiff() {
		return ((sourceOnly == null || sourceOnly.size() == 0) && 
				(targetOnly == null || targetOnly.size() == 0) && 
				subDirDiffNodes.size() == 0 && objDiffs.size() == 0 );
	}

	public StringBuilder diffPrint(String indent) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		StringBuilder stringBuilder = new StringBuilder();
		if (sourceOnly != null && sourceOnly.size() > 0) {
			stringBuilder.append(indent + "  ").append("Source Only: ");
			Object[] objs = sourceOnly.toArray();
			for (int i = 0; i < objs.length; i++) {
				stringBuilder.append(getValueForPrint(objs[i]));
				if (i != objs.length -1)
					stringBuilder.append(", ");
			}
			stringBuilder.append('\n');
		}
		if (targetOnly != null && targetOnly.size() > 0) {
			stringBuilder.append(indent + "  ").append("Target Only: ");
			Object[] objs = targetOnly.toArray();
			for (int i = 0; i < objs.length; i++) {
				stringBuilder.append(getValueForPrint(objs[i]));
				if (i != objs.length -1)
					stringBuilder.append(", ");
			}
			stringBuilder.append('\n');
		}
		return stringBuilder;
	}

}
