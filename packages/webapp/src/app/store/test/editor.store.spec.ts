import { ConfigFile, Configuration, FileTypes } from "models/config-file";
  fileType: FileTypes.YAML,
  fileType: FileTypes.YAML,
    ctx.store.dispatch(new PageLoad({ fileName: null, applicationName: "app1", editMode: false, fileType: FileTypes.YAML }));
      fileType: FileTypes.YAML,
      fileName: "test.yaml", applicationName: "app1", fileType: FileTypes.YAML, originalConfig: new Configuration()
    ctx.store.dispatch(new PageLoad({ fileName: "test.yaml", applicationName: "app1", editMode: true, fileType: FileTypes.YAML }));
      fileName: "test.yaml", applicationName: "app1", fileType: FileTypes.YAML, originalConfig: new Configuration()
    ctx.store.dispatch(new PageLoad({ fileName: "test.yaml", applicationName: "app1", editMode: true, fileType: FileTypes.YAML }));
    ctx.store.dispatch(new PageLoad({ fileName: null, applicationName: "app1", editMode: true, fileType: FileTypes.YAML }));
    ctx.store.dispatch(new PageLoad({ fileName: null, applicationName: "app1", editMode: false, fileType: FileTypes.YAML }));
    ctx.store.dispatch(new PageLoad({ fileName: null, applicationName: "app1", editMode: true, fileType: FileTypes.YAML }));