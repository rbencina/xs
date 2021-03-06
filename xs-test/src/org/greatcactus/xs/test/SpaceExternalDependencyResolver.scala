/**
 * Copyright 2013 Andrew Conway. All rights reserved
 */
package org.greatcactus.xs.test

import java.nio.file.Paths
import java.nio.file.Files
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.WatchService
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.WatchKey
import scala.collection.JavaConversions._
import org.greatcactus.xs.util.ObsoletableAndInterruptableFuture
import org.greatcactus.xs.util.InterruptableFuture
import org.greatcactus.xs.impl.XSExecutionContext
import org.greatcactus.xs.util.ChangeHandle
import org.greatcactus.xs.util.ConventionalChangeHandle

/**
 * @author Andrew
 *
 */
object SpaceExternalDependencyResolver {
  
  val HistoryFileClass = classOf[HistoryFile]
  
  val baseDir : Path = Paths.get("""C:\tmp""")
  
  def resolve(filename:String) : ObsoletableAndInterruptableFuture[HistoryFile] = {
    if (filename==null || filename.isEmpty()) null
    else {
      println("Loading history "+filename)
      val path = baseDir.resolve(filename)
      val handle = new ConventionalChangeHandle()
      val watcher = FSWatcher.watchFileForChanges(path,handle.change)
      handle.addDisposalFunction(watcher)
      val intf = InterruptableFuture.future{
       // println("Looking at path "+path)
        if (Files.isReadable(path)&&Files.isRegularFile(path)) {
          //println("Looks good... "+path)
          try {
            val lines = Files.readAllLines(path,Charset.forName("UTF-8")).toArray(new Array[String](0)) 
            //println("Found history file "+lines.mkString("\n"))
            new HistoryFile(lines)
          } catch {case e:Exception => e.printStackTrace(); throw e }
        } else null
      }(XSExecutionContext.context)
      new ObsoletableAndInterruptableFuture(intf,List(handle))
    }   
  }
  
 
}

object FSWatcher {
  /** Look for a change to the given file. When there is a change, call onchange() and stop looking. Return a function that closes the file. */
  def watchFileForChanges(path:Path,onchange: ()=>Unit) : ()=>Unit = {
     val watcher = new FSWatcher(path.getParent,path,onchange)
     watcher.close _
  }
}

class FSWatcher(directory:Path,file:Path,onchange: ()=>Unit) extends Runnable {
  var closed = false;
  val watcherService : WatchService  = FileSystems.getDefault().newWatchService();
  val watcherKey : WatchKey = directory.register(watcherService,ENTRY_CREATE,ENTRY_DELETE,ENTRY_MODIFY); 
  
  val thread = new Thread(this);
  thread.setDaemon(true)
  thread.start();
  
  def close() { closed=true; thread.interrupt(); watcherKey.cancel()  }
  
  def run() {
    while (!closed) {
      try {
        val key = watcherService.take();
        for (event<-key.pollEvents) event.context match {
          case filename:Path =>
            //println("Found change to "+filename)
            val child = directory.resolve(filename).normalize();
            if (child==file.normalize()) {
              onchange()
              close()
            }
          case c => println("FSWatcher: unexpected result "+c)
        }
        key.reset()
      } catch { case e:InterruptedException => return }
    }
  }
}

