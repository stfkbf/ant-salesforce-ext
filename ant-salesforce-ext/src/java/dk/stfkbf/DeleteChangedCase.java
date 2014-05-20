package dk.stfkbf;

import java.io.File;

public class DeleteChangedCase {

	private String targetPath;
	private String sourcePath;
	private String gitRoot;

	public String getTargetPath() {
		return targetPath;
	}

	public void setTargetPath(String targetPath) {
		this.targetPath = targetPath;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public String getGitRoot() {
		return gitRoot;
	}

	public void setGitRoot(String gitRoot) {
		this.gitRoot = gitRoot;
	}

	public static void main(String[] args){
		DeleteChangedCase files = new DeleteChangedCase();
		System.out.println("Checking for case changes");
		files.setSourcePath(args[0]);
		files.setTargetPath(args[1]);
		files.setGitRoot(args[2]);
		files.execute();
	}

	public void execute(){
		try {
			traverse(new File(this.getTargetPath()));
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void traverse(File file) throws Exception{		
		File targetDir = file;
		
		File sourcePath = new File(this.getSourcePath());
		File targetPath = new File(this.getTargetPath());
		
		if (file.exists()){
			for (String currentFile : targetDir.list()){
				File targetFile = new File(targetDir, currentFile);

				File sourceFile = new File(targetFile.getAbsolutePath().replace(targetPath.getAbsolutePath(), sourcePath.getAbsolutePath()));

				if (!targetFile.getCanonicalFile().getName().equals(sourceFile.getCanonicalFile().getName())){
					System.out.println("Casing changed: " + targetFile.getName() + " " + sourceFile.getName());
					targetFile.delete();
				}

				if (targetFile.isDirectory()){
					traverse(targetFile);
				}
			}		
		}
	}

}
