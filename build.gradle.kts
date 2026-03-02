allprojects {
    tasks.register("resolveDeps") {
        doLast {
            configurations.forEach { cfg ->
                if (cfg != null && cfg.isCanBeResolved)
                    cfg.resolve()
            }
        }
    }
}

