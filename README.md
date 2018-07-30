# BukkitLoader

Allows developers to instrument their own loading code into the Bukkit plugin loader

I used Javassist to modify the bytecode of the Bukkit PluginClassLoader and Xyene's Late-Bind-Agent to re-instrument the modified bytecode back into the class loader.
