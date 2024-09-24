rework this
- description
- setup
  - plugins
  - server (link to separate file)
- dev (link to separate file)
- credits
- licensing

# Enigma

A tool for deobfuscation of Java bytecode. Forked from <https://bitbucket.org/cuchaz/enigma>, originally created by [Jeff Martin](https://www.cuchazinteractive.com/).

## License

Enigma is distributed under the [LGPL-3.0](LICENSE).

Enigma includes the following open-source libraries:
 - [Vineflower](https://github.com/Vineflower/vineflower) (Apache-2.0)
 - [Procyon](https://github.com/mstrobel/procyon) (Apache-2.0)
 - A [modified version](https://github.com/fabricmc/cfr) of [CFR](https://github.com/leibnitz27/cfr) (MIT)
 - [Guava](https://github.com/google/guava) (Apache-2.0)
 - [SyntaxPane](https://github.com/Sciss/SyntaxPane) (Apache-2.0)
 - [FlatLaf](https://github.com/JFormDesigner/FlatLaf) (Apache-2.0)
 - [jopt-simple](https://github.com/jopt-simple/jopt-simple) (MIT)
 - [ASM](https://asm.ow2.io/) (BSD-3-Clause)

## Usage

Pre-compiled jars can be found on the [Quilt maven](https://maven.quiltmc.org/repository/release/org/quiltmc/).

### Launching the GUI

`java -jar enigma.jar`

### On the command line

`java -cp enigma.jar org.quiltmc.enigma.command.Main`

## development

Enigma is split up into 4 components:
- `enigma`, which is the core of the project. This handles essential services such as remapping, mapping read/write, decompilation, plugins, and indexing.
- `enigma-cli`, which depends on `enigma` to provide commands to work with mappings.
- `enigma-server`, which depends on `enigma` to provide a TCP socket server allowing online collaboration in remapping.
- `enigma-swing`, which serves as a [swing](https://docs.oracle.com/javase/tutorial/uiswing/) frontend for `enigma` with `enigma-server` integration.

### enigma

#### plugins

Enigma's core is built around a plugin system. Our goal as developers is to abstract as much functionality as possible into these plugins, which are comprised of several *services* each. The types of services currently usable are:
- `DecompilerService`: a service that implements a decompiler to provide visual representation of sources for frontends.
- `JarIndexerService`: a service that assembles information on the main project and its libraries for other features to use.
- `ReadWriteService`: a service that implements a reader and/or writer for saving mappings to the disk.
- `ObfuscationTestService`: a service that tests any given entry to see if it is deobfuscated.
- `NameProposalService`: a service that provides a name for an obfuscated entry, using data from the bytecode and other mappings.

These services are assembled into plugins and configured using an *enigma profile*. This profile looks something like this:
```json
{
	"services": {
		"jar_indexer": [
			{
				"id": "enigma:enum_initializer_indexer"
			}
		],
		"name_proposal": [
			{
				"id": "enigma:enum_name_proposer"
			}
		]
	}
}
```

The profile does two things:
- Dictates which services are enabled: most services are disabled by default (each service type's javadoc specifies if it is enabled by default or not), and must be manually toggled on by the profile. Providing the `id` like in the above example enables the service.
- Provides arguments for the services: when a service is registered to a plugin, it is provided the context of everything in its JSON block. This means that services can use that `EnigmaServiceContext<EnigmaService>` object to check for arguments that provide context for its functionality.

#### entries

The way that Enigma represents elements of the code being mapped is through `Entry` objects. These entry objects will be instantiated as `ParentedEntry` objects, which usually reference their containing class as the parent. The `Entry` objects that you'll encounter are:
- `ClassEntry`: represents a class. Importantly, this class can be either a top-level class, with no parent, or an inner class, in which case the parent will be the class one level above.
- `FieldEntry`: represents a field. Contains a `desc` object that encodes the type of the field.
- `MethodEntry`: represents a method. Contains a `desc` object that encodes both the arguments and return type of the method. Importantly, does not contain references to each argument, meaning the obfuscated entries for each parameter cannot be accessed with just a method entry.
- `LocalVariableEntry`: represents a local variable or a parameter. Is parented by a `MethodEntry` instead of a `ClassEntry`. Contains a boolean for whether it is a parameter and, if that boolean is true, an index representing its position in the arguments for its parent method. Note that if non-static, this index will be shifted by `1` as the first argument is always an instance of the method's parent class.

Entries also have a `DefEntry` form, that provides additional information. Def entries (excluding local variable entries) always provide access flags which give properties such as the entry's access, whether it's static, etc. They are accessed via the `EntryIndex`, which can be received from a project with the code `project.getJarIndex().getIndex(EntryIndex.class)`.

Important to note is that when getting mappings via an `EntryRemapper` object, you'll sometimes get an empty mapping for an entry that is mapped. This usually occurs when you're working with inheritance: as each entry is parented by a specific class, only the root entry will own the real mapping. For entries parented by inheriting classes, you can use an instance of `IndexEntryResolver` with the `#resolveEntry` method to find the root entry and obtain the mapping.

#### indexing

Enigma assembles information about the main project in the `JarIndex`, which can be accessed via `EnigmaProject#getJarIndex`.

Available indexes can be found in `org.quiltmc.enigma.api.analysis.index.jar` package, and gotten from a `JarIndex` object via `JarIndex#getIndex(Class<T>)`.

#### testing

The main way that enigma's core is developed is through unit tests. Enigma's test system compiles inputs into jar files which are sent as input into tests. Using this system, most tests will be set up like this:
```java
public static final Path JAR = TestUtil.obfJar("JAR_NAME");
private static EnigmaProject project;

@BeforeAll
static void setupEnigma() throws IOException {
	Enigma enigma = Enigma.create();
	project = enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.createEmpty());
}
```

`JAR_NAME` will be replaced with one of the packages in the `org.quiltmc.enigma.input` package. This allows you to set up classes that will reproduce the issue or demonstrate the feature you're developing, then pass them into a project to be tested. When you create a new package inside the `input` package, all classes inside that package will be automatically compiled into a jar. A gradle task will also be created to pass that jar into `enigma-swing` for other testing.

This isn't the only way to develop enigma, but we appreciate it, and it makes sure your changes won't break in the future!

#### `Enigma` vs `EnigmaProject`

Two important classes in enigma are `Enigma` and `EnigmaProject`. These two, despite the similar naming, are completely different:
- `Enigma` deals with setting up the program itself: it reads the profile and assembles the plugin services. This is where you'll go if you need to find a service.
- `EnigmaProject` deals with project-specific information such as the index of the current jar and the current mappings.

#### mapping storage

- EntryTree
- the wild proposal system
