pdi-scriptengine-plugin
=======================

A PDI plugin like the Script step but with easier support for more JSR-223 scripting engines and other improvements, such as:

- Drop-down UI for selecting the scripting engine
- Support for arguments passed to the script (rather than variable bindings)
- Snippets of Groovy code (since the Groovy script engine is available in PDI)
- Removal of JavaScript-specific code (the Modified Java Script Value step is the alternative, although this step does support the JavaScript scripting engine)
- Ability to function as a data generator (aka input step), no incoming rows needed for scripts to be run


I'm also looking at including Ivy within, specifically for Groovy Grape support. This will add the ability for scripts to resolve their dependencies at execution time and should help with deployments of this step on clustered and remote environments.

Always open to more suggestions and of course, contributions!
