package dk.stfkbf;

import java.io.File;
import java.util.Set;

import org.eclipse.jgit.api.Git;

public class DeleteFromGit {

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
		DeleteFromGit files = new DeleteFromGit();

		files.setSourcePath(args[0]);
		files.setTargetPath(args[1]);
		files.setGitRoot(args[2]);
		files.execute();
	}

	public void execute(){
		try {
			Git git = Git.open(new File(this.getGitRoot()));

			traverse(git, new File(this.getTargetPath()));
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void traverse(Git git, File file) throws Exception{		
		File targetDir = file;
		
		File sourcePath = new File(this.getSourcePath());
		File gitRootPath = new File(this.getGitRoot());
		File targetPath = new File(this.getTargetPath());
		
		if (file.exists()){
			for (String currentFile : targetDir.list()){
				File targetFile = new File(targetDir, currentFile);

				File sourceFile = new File(targetFile.getAbsolutePath().replace(targetPath.getAbsolutePath(), sourcePath.getAbsolutePath()));

				String fileName = targetFile.getAbsolutePath().replace(gitRootPath.getAbsolutePath() + "/", "");

				if (targetFile.isFile() &&  !sourceFile.exists()) {
					Set<String> list = git.status().addPath(fileName).call().getIgnoredNotInIndex();

					if (!list.contains(fileName)){
						System.out.println("Deleted: " + fileName);
						git.rm().addFilepattern(fileName).call();
					}
				}

				if (targetFile.isDirectory()){
					traverse(git, targetFile);
				}
			}		
		}
	}

}
