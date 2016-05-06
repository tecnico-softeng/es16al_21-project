package pt.tecnico.myDrive.domain;

import pt.tecnico.myDrive.visitors.GenericVisitor;
import pt.tecnico.myDrive.visitors.DirectoryVisitor;

import org.jdom2.Element;
import java.io.UnsupportedEncodingException;
import org.jdom2.DataConversionException;

import pt.tecnico.myDrive.exceptions.UserUnknownException;
import pt.tecnico.myDrive.exceptions.ImportDocumentException;
import pt.tecnico.myDrive.exceptions.FileUnknownException;
import pt.tecnico.myDrive.exceptions.IllegalRemovalException;
import pt.tecnico.myDrive.exceptions.NotADirectoryException;
import pt.tecnico.myDrive.exceptions.FileUnknownException;
import pt.tecnico.myDrive.exceptions.FileExistsException;
import pt.tecnico.myDrive.exceptions.MethodDeniedException;

import pt.tecnico.myDrive.exceptions.InsufficientPermissionsException;

import java.util.*;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.RuntimeException;

public class Directory extends Directory_Base {

  /** Placeholder for FenixFramework */
  protected Directory () {
    super();
  }

  public Directory(FileSystem fs, String name, Directory parent, User owner) {
    init(fs, fs.requestId(), name, parent, owner);
  }

  /**
   * A top-level directory is a directory which its parent is itself.
   * An example of a top-level directory would be the root directory.
   *
   * @return true if the directory is a top-level directory.
   */
  public boolean isTopLevelDirectory() { return getParent() == this; }

  /**
   * Get a file inside the directory by its name.
   *
   * @param filename
   * @return The file which name is filename.
   */
  public File getFileByName(String filename) {
    if(filename.equals("."))
      return this;
    else if(filename.equals(".."))
      return getParent();
    else
      for (File file : super.getFileSet()) {
        if (filename.equals(file.getName()))
          return file;
      }
    throw new FileUnknownException(filename);
  }

  @Override
  public File getFile(ArrayList<String> tokens, User user) {
    // Terminal case
    File file;
    if(tokens.size() > 1){
      String name = tokens.remove(0);
      file = getFileByName(name).getFile(tokens, user);
      file.checkExecutionPermissions(user);
      return file;
    }else if(tokens.size() == 1){
      file = getFileByName(tokens.get(0)).getFileObject(user);
      file.checkReadPermissions(user);
      return file;
    }else{
      throw new RuntimeException("Shouldn't happen! (Wrongly formated token array in get File)");
    }
  }
  /**
   * The path of a directory is a string that specifies how to reach itself by
   * going through other directories in a filesystem.
   *
   * @return The string corresponding to the path the directory.
   */

  @Override
  public String getPath() { return isTopLevelDirectory() ? getName() : getPathHelper(); }

  /**
   * Simple helper function to call when the path needs to be processed
   */
  private String getPathHelper() {
    return (isTopLevelDirectory()) ? "" : getParent().getPathHelper()+ "/" + getName();
  }

  /**
   * @return Lists the files inside the directory using only their name.
   */
  private String listFilesSimple() {
    Comparator<File> comp = new Comparator<File>()
    {
      public int compare(File f1, File f2)
      {
        return f1.toString().compareTo(f2.toString());
      }
    };
    try {
      return listFilesGenericSorted(Class.forName("pt.tecnico.myDrive.domain.File").getMethod("getName"), comp);
    } catch(NoSuchMethodException | ClassNotFoundException e){
      throw new RuntimeException("Unknown method or class on list file");
    }
  }

  /**
   * @return List of the files inside the directory using their toString method.
   */
  public String listFilesAll(User user) {
    checkReadPermissions(user);

    Comparator<File> comp = new Comparator<File>()
    {
      public int compare(File f1, File f2)
      {
        return f1.getName().compareTo(f2.getName());
      }
    };
    try {
      return listFilesGenericSorted (Class.forName("pt.tecnico.myDrive.domain.File").getMethod("toString"), comp);
    } catch(NoSuchMethodException | ClassNotFoundException e){
      throw new RuntimeException("Unknown method or class on list file");
    }
  }

  /**
   * List files in a generic way.
   * The way the listing is done is by applying method to the files, hence,
   * the list will be of the form:
   *
   * apply(method, file1)
   * apply(method, file2)
   * ...
   * apply(method, fileN)
   *
   * @param method
   * @return A list containing the description given my method
   * of the files inside the directory.
   */
  private String listFilesGeneric (Method method) {
    /**
     * Replaces the string generated by the application of 'method' to the
     * 'this' directory and parent directories' respectively for "." and ".."
     */
    try {
      String self = ((String) method.invoke(this)).replaceAll(getName(), ".") + "\n";
      String parent = ((String) method.invoke(getParent())).replaceAll(getParent().getName(), "..") + "\n";
      String list = self + parent;
      for (File file: super.getFileSet())
        list += method.invoke(file) + "\n";
      return list;
    } catch(InvocationTargetException e) {
      throw new RuntimeException("InvocationTargetException on list file");
    } catch(IllegalAccessException e) {
      throw new RuntimeException("IllegalAccessException on list file");
    }
  }

  /**
   * List files in a generic way and sorted according to a Comparator.
   * The files are first sorted using the Comparator and then listed.
   * The way the listing is done is by applying method to the files, hence,
   * the list will be of the form:
   *
   * apply(method, file1)
   * apply(method, file2)
   * ...
   * apply(method, fileN)
   *
   * @param method
   * @param comparator
   * @return A sorted list containing the description given my method
   * of the files inside the directory.
   */
  private String listFilesGenericSorted (Method method, Comparator<File> comparator) {
    /**
     * Replaces the string generated by the application of 'method' to the
     * 'this' directory and parent directories' respectively for "." and ".."
     */
    try {
      String self = ((String) method.invoke(this)).replaceAll(getName(), ".") + "\n";
      String parent = ((String) method.invoke(getParent())).replaceAll(getParent().getName(), "..") + "\n";
      String list = self + parent;
      TreeSet<File> tree = new TreeSet<File>(comparator);
      for (File file: super.getFileSet())
        tree.add(file);
      for(File file: tree)
        list += method.invoke(file) + "\n";
      return list;
    } catch(InvocationTargetException e) {
      throw new RuntimeException("InvocationTargetException on list file");
    } catch(IllegalAccessException e) {
      throw new RuntimeException("IllegalAccessException on list file");
    }
  }

  /**
   * The size of a directory is given by the number of files inside it.
   *
   * @return The size of a directory.
   */
  @Override
  public int getSize() { return 2 + super.getFileSet().size(); }

  /**
   * @return True if file is in directory, false otherwise.
   */
  private boolean hasFile(String filename) {
    for (File file : super.getFileSet())
      if (filename.equals(file.getName()))
        return true;
    return false;
  }

  /**
   * Removes all files in a directory
   */
  protected void removeAllFiles() {
    for (File file : super.getFileSet())
      file.remove();
  }

  public void remove(String filename, User user) {
    checkIllegalRemoval(filename);
    File file = getFileByName(filename);
    file.remove(user);
  }

  @Override
  public Set<File> getFileSet() {
    throw new MethodDeniedException();
  }

  @Override
  public void addFile(File file) {
    throw new MethodDeniedException();
  }

  @Override
  public void removeFile(File file) {
    throw new MethodDeniedException();
  }

  @Override
  protected void remove() {
    removeAllFiles();
    nullifyRelations();
    deleteDomainObject();
  }

  @Override
  protected void nullifyRelations() {
    super.nullifyRelations();
    setUser(null);
  }

  /**
   * @return True if directory has no files other than itself and its parent.
   */
  public boolean isEmpty() {
    return getSize() == 2;
  }

  @Override
  public String execute(User user) {
    String s = "Couldn't list directory.";
    try{
      s = listFilesAll(user);
    }catch(Exception e){
      System.out.println("-- Error executing directory: " + e.getMessage());
    }
    return s;
  }

  public void checkIllegalRemoval(String filename) {
    if (filename.equals(".") || filename.equals(".."))
      throw new IllegalRemovalException();
  }

  @Override
  public <T> T accept(GenericVisitor<T> v){
    return v.visit(this);
  }

  public void xmlImport(Element dirElement) throws ImportDocumentException {
    try{
      setId(dirElement.getAttribute("id").getIntValue());

      Element perm = dirElement.getChild("perm");
      if (perm != null)
        setUserPermission(new String(perm.getText().getBytes("UTF-8")));


    } catch(UnsupportedEncodingException | DataConversionException e){
      throw new ImportDocumentException(String.valueOf(getId()));
    }
  }

  @Override
  public String toString(){
    return "d " + getUserPermission() + getOthersPermission() + " " + getName();
  }

   /* ****************************************************************************
   *  |                     Public File creation methods                         |
   *  ****************************************************************************
   */

  public Directory createDirectory(String name, User owner){
    checkWritePermissions(owner);
    checkFileUnique(name);
    return new Directory(getFileSystem(), name, this, owner);
  }

  public PlainFile createPlainFile(String name, User owner){
    checkWritePermissions(owner);
    checkFileUnique(name);
    return new PlainFile(getFileSystem(), name, this, owner);
  }

  public PlainFile createPlainFile(String name, User owner, String data){
    checkWritePermissions(owner);
    checkFileUnique(name);
    PlainFile pf = new PlainFile (getFileSystem(), name, this, owner);
    pf.setData(data, owner);
    return pf;
  }

  public App createApp(String name, User owner){
    checkWritePermissions(owner);
    checkFileUnique(name);
    return new App(getFileSystem(), name, this, owner);
  }

  public Link createLink(String name, User owner, String data){
    checkWritePermissions(owner);
    checkFileUnique(name);
    return new Link(getFileSystem(), name, this, owner, data);
  }

  /**
   * Verifies if name is unique in Directory
   * @param name
   */
  private void checkFileUnique(String name) {
    if(hasFile(name)) throw new FileExistsException(name);
  }
}
