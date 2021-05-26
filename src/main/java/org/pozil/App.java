package org.pozil;

import apex.jorje.data.ast.BlockMember;
import apex.jorje.data.ast.BlockMember.InnerClassMember;
import apex.jorje.data.ast.BlockMember.PropertyMember;
import apex.jorje.data.ast.ClassDecl;
import apex.jorje.data.ast.CompilationUnit;
import apex.jorje.data.ast.CompilationUnit.ClassDeclUnit;
import apex.jorje.data.ast.PropertyDecl;
import apex.jorje.data.ast.PropertyGetter;
import apex.jorje.data.ast.PropertySetter;
import apex.jorje.data.ast.Modifier;
import apex.jorje.data.ast.Modifier.Annotation;
import apex.jorje.data.ast.Modifier.PrivateModifier;
import apex.jorje.semantic.compiler.SourceFile;
import apex.jorje.semantic.compiler.parser.ParserEngine;
import apex.jorje.semantic.compiler.parser.ParserOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.LogManager;

public class App {

	private static final String AURA_ENABLED = "AuraEnabled";
	private static final String APEX_FILE_EXTENSION = ".cls";

	private ParserEngine engine;
	private List<String> path;
	private int apexFileCounter;
	private int matchCount;

	public App() {
		this.engine = ParserEngine.get(ParserEngine.Type.NAMED);
		this.path = new ArrayList<>();
		this.apexFileCounter = 0;
		this.matchCount = 0;
	}

	private void scan(File rootDir) throws IOException {
		this.scanDirectory(rootDir);
		System.out.printf("%nScanned %d Apex files and found %d potential matches.%n", this.apexFileCounter, this.matchCount);
	}
	
	public void scanDirectory(File dir) throws IOException {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				scanDirectory(file);
			} else if (file.isFile() && file.getName().toLowerCase().endsWith(APEX_FILE_EXTENSION)) {
				this.apexFileCounter ++;
				parseApexFile(file.toPath());
			}
		}
	}
	
	private void parseApexFile(Path filePath) throws IOException {
		try {
			String sourceCode = Files.readString(filePath);
			SourceFile sourceFile = SourceFile.builder().setBody(sourceCode).build();
			ParserOutput output = engine.parse(sourceFile, ParserEngine.HiddenTokenBehavior.IGNORE);

			CompilationUnit unit = output.getUnit();
			if (unit instanceof ClassDeclUnit) {
				parseClassDeclaration(((ClassDeclUnit) unit).body);
			}
		} catch (Exception e) {
			throw new IOException("Failed to parse \""+ filePath +"\": "+ e.getMessage(), e);
		}
	}

	private void parseClassDeclaration(ClassDecl classDecl) {
		String className = classDecl.name.getValue();
		this.path.add(className);
		if (classDecl.members != null) {
			classDecl.members.forEach(member -> {
				parseBlockMember(member);
			});
		}
		this.path.remove(this.path.size() - 1);
	}

	private void parseBlockMember(BlockMember member) {
		if (member instanceof PropertyMember) {
			parsePropertyDeclaration(((PropertyMember) member).propertyDecl);
		} else if (member instanceof InnerClassMember) {
			parseClassDeclaration(((InnerClassMember) member).body);
		}
	}

	private void parsePropertyDeclaration(PropertyDecl property) {
		if (hasAuraEnabledModifier(property.modifiers)
				&& (hasPrivateGetter(property.getter) || hasPrivateSetter(property.setter))) {
			this.matchCount ++;
			String name = property.name.getValue();
			this.path.add(name);
			System.out.println(String.join(".", this.path));
			this.path.remove(this.path.size() - 1);
		}
	}

	private boolean hasAuraEnabledModifier(List<Modifier> modifiers) {
		Modifier foundModifier = modifiers.stream().filter(modifier -> {
			if (modifier instanceof Annotation) {
				return AURA_ENABLED.equals(((Annotation) modifier).name.getValue());
			}
			return false;
		}).findAny().orElse(null);
		return foundModifier != null;
	}

	private boolean hasPrivateSetter(Optional<PropertySetter> optionalSetter) {
		try {
			Modifier mod = optionalSetter.get().modifier.get();
			return mod instanceof PrivateModifier;
		} catch (NoSuchElementException e) {
			return false;
		}
	}

	private boolean hasPrivateGetter(Optional<PropertyGetter> optionalGetter) {
		try {
			Modifier mod = optionalGetter.get().modifier.get();
			return mod instanceof PrivateModifier;
		} catch (NoSuchElementException e) {
			return false;
		}
	}
	
	public static void main(String[] args) throws IOException {
		// Disable jorje logs
		LogManager.getLogManager().reset();
		
		// Check parameters
		if (args.length != 1) {
			System.out.println();
			System.out.println("Recursively scans a directory for Apex files then report patterns that are affected by the Summer '21 security update:");
			System.out.println("Enforce Access Modifiers on Apex Properties in Lightning Component Markup");
			System.out.println("https://help.salesforce.com/articleView?id=release-notes.rn_lc_enforce_prop_modifiers_cruc.htm&type=5&release=232");
			System.out.println();
			System.out.println("Usage:\tjava -jar apex-scan-1.0.0.jar path");
			System.out.println("\tpath\tpath of root directory");
			System.out.println();
			System.exit(-1);
			return;
		}
		
		// Check root directory
		File rootDir;
		try {
			rootDir = new File(args[0]);
			if (!rootDir.exists()) {
				throw new Exception("path does not exist");
			}
			if (!rootDir.isDirectory()) {
				throw new Exception("path does not denote a directory");
			}
		} catch (Exception e) {
			System.err.printf("ERROR: Could not read directory \"%s\": %s%n", args[0], e.getMessage());
			System.exit(-2);
			return;
		}
		// Run scan
		try {
			App app = new App();
			app.scan(rootDir);
		} catch (Exception e) {
			System.err.printf("ERROR: %s%n", e.getMessage());
			System.exit(-3);
			return;
		}
		
	}
}