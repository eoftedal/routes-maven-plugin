package no.oftedal;

import javax.ws.rs.*;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.reflections.Configuration;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


@Mojo( name = "routes", defaultPhase = LifecyclePhase.NONE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME )
public class RoutesMojo extends AbstractMojo
{	

    @Parameter( property = "scanpackages", required = true )
    private String scanpackages;
    
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    
    public void execute()
    {
    	try {
			System.out.println("Scanning " + scanpackages + " ...");
	
			Reflections reflections = loadClasses();
			
	    	Set<Class<?>> resourceClasses = reflections.getTypesAnnotatedWith(Path.class);
	    	
	    	for ( Method m : reflections.getMethodsAnnotatedWith(Path.class)) {
	    		resourceClasses.add(m.getDeclaringClass());
	    	}
	    	List<Route> routes = new ArrayList();
	    	for (Class<?> c : resourceClasses) {
	    		String basePath = "";
	    		if (c.isAnnotationPresent(Path.class)) {
	    			basePath = c.getAnnotation(Path.class).value();
	    		}
	    		for (Method m : ReflectionUtils.getAllMethods(c, ReflectionUtils.withModifier(Modifier.PUBLIC))) {
	    			String methodPath = basePath;
	        		if (m.isAnnotationPresent(Path.class)) {
	        			String p = m.getAnnotation(Path.class).value();
	        			if (p.startsWith("/")) p = p.substring(1);
	        			methodPath += methodPath.endsWith("/") ? p : ("/" + p);
	        		}
	        		List<String> verbs = new ArrayList<String>();
	        		if (m.isAnnotationPresent(GET.class)) verbs.add("GET");
	        		if (m.isAnnotationPresent(POST.class)) verbs.add("POST");
	        		if (m.isAnnotationPresent(PUT.class)) verbs.add("PUT");
	        		if (m.isAnnotationPresent(DELETE.class)) verbs.add("DELETE");
	        		if (m.isAnnotationPresent(OPTIONS.class)) verbs.add("OPTIONS");
	        		if (m.isAnnotationPresent(HEAD.class)) verbs.add("HEAD");
	        		if (verbs.isEmpty()) continue;
	    			for (String verb : verbs) {
	    				routes.add(new Route(verb, methodPath, m));
	    			}
	    		}
	    	}
	    	Collections.sort(routes);
	    	int longestPath = 0;
	    	for (Route route : routes) {
	    		if (route.path.length() > longestPath) longestPath = route.path.length();
	    	}
	    	for (Route route : routes) {
	    		System.out.println(route.beautify(longestPath));
	    	}
    	} catch (Exception ex) {
    		ex.printStackTrace(System.err);
    		if (ex.getCause() != null) {
    			ex.getCause().printStackTrace(System.err);
    		}
    		
    	}
    }

	private Reflections loadClasses() {
		try {
			Set<URL> urls = new HashSet<>();
			List<String> elements = new ArrayList<>();
			elements.addAll(project.getRuntimeClasspathElements());
			elements.addAll(project.getCompileClasspathElements());
			elements.addAll(project.getSystemClasspathElements());
		    for (String element : elements) {
		        urls.add(new File(element).toURI().toURL());
		    }
		    for(Object o : project.getDependencyArtifacts()) {
		    	Artifact a = (Artifact)o;
		    	if (a.getFile() != null) urls.add(a.getFile().toURI().toURL());
		    }
		    for(Object o : project.getArtifacts()) {
		    	Artifact a = (Artifact)o;
		    	if (a.getFile() != null) urls.add(a.getFile().toURI().toURL());
		    }
		    for(Object o : project.getAttachedArtifacts()) {
		    	Artifact a = (Artifact)o;
		    	if (a.getFile() != null) urls.add(a.getFile().toURI().toURL());
		    }

		    ClassLoader contextClassLoader = URLClassLoader.newInstance(
		            urls.toArray(new URL[0]),
		    		ClasspathHelper.contextClassLoader());
		    ConfigurationBuilder config = new ConfigurationBuilder()
	    		.filterInputsBy(new FilterBuilder().includePackage(scanpackages))
		    	.setUrls(ClasspathHelper.forClassLoader(contextClassLoader))
		    	.setScanners(new MethodAnnotationsScanner(), new TypeAnnotationsScanner(), new SubTypesScanner());
			config.setClassLoaders(new ClassLoader[] { contextClassLoader, this.getClass().getClassLoader() });
	    	
	    	Reflections reflections = new Reflections(config);
	    	return reflections;
		} catch(DependencyResolutionRequiredException | MalformedURLException e) {
			throw new RuntimeException("Failed to load classes", e);
		}
	}
	private static class Route implements Comparable<Route>{
		private static List<String> order = Arrays.asList("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS");
		public String path;
		public Method method;
		public String verb;
		
		public Route(String verb, String path, Method method) {
			this.verb = verb;
			this.path = path;
			this.method = method;
			
		}
		
		@Override
		public int compareTo(Route o) {
			int j = path.compareTo(o.path);
			if (j != 0) return j;
			j = compareVerbs(verb, o.verb);
			if (j != 0) return j;
			return method.getName().compareTo(o.method.getName());
		}
		private int compareVerbs(String v1, String v2) {
			return toInt(v1) - toInt(v2);
		}
		private int toInt(String verb) {
			return order.indexOf(verb);
		}
		
		
		
		public String beautify(int padding) {
			return rpad(verb, 8) + rpad(path, padding) + " - " + method.getDeclaringClass().getName() + "." + method.getName() + "(...)";
		}
		private String rpad(String v, int num) {
			StringBuilder b = new StringBuilder(v);
			for (int i = b.length(); i < num; i++) b.append(" ");
			return b.toString();
		}
	}
    
}
