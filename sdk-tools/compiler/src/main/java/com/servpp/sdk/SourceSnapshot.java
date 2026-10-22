package com.servpp.sdk;
import java.nio.file.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/** One private input tree is used both for project inspection and generation. Never published. */
public final class SourceSnapshot {
    public static Path capture(Path source,Path destination)throws Exception {
        try(var paths=Files.walk(source)) {
            var entries=paths.limit(10001).sorted().toList();
            if(entries.size()>10000)throw invalid();
            Files.createDirectory(destination);
            for(var entry:entries) {
                if(Files.isSymbolicLink(entry) || !entry.toRealPath().startsWith(source))throw invalid();
                Path target=destination.resolve(source.relativize(entry));
                if(Files.isDirectory(entry,LinkOption.NOFOLLOW_LINKS)){Files.createDirectories(target);continue;}
                if(!Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS))throw invalid();
                Files.copy(entry,target,LinkOption.NOFOLLOW_LINKS);
                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw invalid();
            }
        }
        return destination;
    }
    public static com.servpp.lang.CompilationResources.Scope resources(Path original,Path snapshot,Path assets) {
        var copies=new HashMap<Path,Path>();
        return com.servpp.lang.CompilationResources.withResolver((file,location)->{
            try {
                Path requested=file.toPath().toAbsolutePath().normalize();
                if(!requested.startsWith(snapshot))throw new IOException("Resource source is outside the private input.");
                Path originalFile=original.resolve(snapshot.relativize(requested));
                Path directory=originalFile.getParent().resolve(location).normalize();
                if(directory.startsWith(original))return snapshot.resolve(original.relativize(directory));
                var previous=copies.get(directory);if(previous!=null)return previous;
                Files.createDirectories(assets);Path copy=assets.resolve(Integer.toString(copies.size()));
                capture(directory.toRealPath(),copy);copies.put(directory,copy);return copy;
            }catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("Cannot snapshot language resources.",failure);}
        });
    }
    public static void remove(Path root)throws IOException {try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    public static String hash(Path file)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}return HexFormat.of().formatHex(digest.digest());
    }
    private static CompilationException invalid(){return new CompilationException("INVALID_SOURCE","Source must contain at most 10000 regular files/directories and no symbolic links.");}
}
