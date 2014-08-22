package totem;

import java.io.File;
import java.io.FileFilter;

public class NonHiddenFileFilter implements FileFilter {

	@Override
	public boolean accept(File file) {
		if (file.getName().startsWith("."))
			return false;
		else{
			if (file.length() < 100) return false;
			else return true;
		}
	}

}
