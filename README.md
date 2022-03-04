# pedestal-native

Main goals:

- Create a GraalVM native-image of an pedestal application
- Understand the build process
- Find and address the pedestal/graalvm limitations

# developing

- Open a REPL with `:dev` profile
- go to `hello.build` and run `(-main)`

`hello.build/-main` function will:
- compile the code via t.d.a
- build a jar via t.d.a
- create config files and run this jar with `native-image-agent`, to generate new config files
- use these new config files to create the final binary, via native-image.
- test if the final binary works

# todo

- [x] do it in github CI
- [ ] do it in github CI macos
- [x] investigate why the binary is huge
- [ ] Use [refl](https://github.com/borkdude/refl/blob/main/script/gen-reflect-config.clj) for better reflect.conf
- [ ] investigate why pedestal.log fails
