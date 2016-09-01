越来越多第三方库使用apt技术，如[DBflow](https://github.com/Raizlabs/DBFlow)、[Dagger2](https://github.com/google/dagger)、[ButterKnife](https://github.com/JakeWharton/butterknife)、[ActivityRouter](https://github.com/joyrun/ActivityRouter)、[AptPreferences](https://github.com/joyrun/AptPreferences)。在编译时根据Annotation生成了相关的代码，非常高大上但是也非常简单的技术，可以给开发带来了很大的便利。

## Annotation
如果想学习APT，那么就必须先了解Annotation的基础，这里附加我另外一篇文章的地址：http://www.taoweiji.cn/2016/07/18/java-annotation
## APT
APT(Annotation Processing Tool)是一种处理注释的工具,它对源代码文件进行检测找出其中的Annotation，使用Annotation进行额外的处理。
      Annotation处理器在处理Annotation时可以根据源文件中的Annotation生成额外的源文件和其它的文件(文件具体内容由Annotation处理器的编写者决定),APT还会编译生成的源文件和原来的源文件，将它们一起生成class文件。

### 创建Annotation Module
首先，我们需要新建一个名称为annotation的Java Library，主要放置一些项目中需要使用到的Annotation和关联代码。这里简单自定义了一个注解：
```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS) 
public @interface Test {   } 
```
##### 配置build.gradle，主要是规定jdk版本

```
apply plugin: 'java'
sourceCompatibility = 1.7 
targetCompatibility = 1.7 
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
}
```



### 创建Compiler Module
创建一个名为compiler的Java Library，这个类将会写代码生成的相关代码。核心就是在这里。
##### 配置build.gradle

```
apply plugin: 'java'
sourceCompatibility = 1.7 
targetCompatibility = 1.7 
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile 'com.google.auto.service:auto-service:1.0-rc2'
    compile 'com.squareup:javapoet:1.7.0'
    compile project(':annotation')
}
```
1. 定义编译的jdk版本为1.7，这个很重要，不写会报错。
2. AutoService 主要的作用是注解 processor 类，并对其生成 META-INF 的配置信息。
3. JavaPoet 这个库的主要作用就是帮助我们通过类调用的形式来生成代码。
4. 依赖上面创建的annotation Module。
#### 定义Processor类
生成代码相关的逻辑就放在这里。

```
@AutoService(Processor.class)
public class TestProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Test.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
```
##### 生成第一个类
我们接下来要生成下面这个HelloWorld的代码：
```
package com.example.helloworld;

public final class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, JavaPoet!");
  }
}
```
修改上述TestProcessor的process方法

```
@Override    
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
   MethodSpec main = MethodSpec.methodBuilder("main")
           .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
           .returns(void.class)
           .addParameter(String[].class, "args")
           .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
           .build();
   TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld")
           .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
           .addMethod(main)
           .build();
   JavaFile javaFile = JavaFile.builder("com.example.helloworld", helloWorld)
           .build();
   try {
       javaFile.writeTo(processingEnv.getFiler());
   } catch (IOException e) {
       e.printStackTrace();
   }
   return false;
}

```

### 在app中使用
##### 配置项目根目录的build.gradle

```
dependencies {
    classpath 'com.neenbedankt.gradle.plugins:android-apt:1.8'
}
```


##### 配置app的build.gradle

```
apply plugin: 'com.android.application'
apply plugin: 'com.neenbedankt.android-apt'
//...
dependencies {
    //..
    compile project(':annotation')
    apt project(':compiler')
}
```
##### 编译使用
在随意一个类添加@Test注解
```
@Test
public class MainActivity extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_main);
   }
}

```

点击Android Studio的ReBuild Project，可以在在app的 `build/generated/source/apt`目录下，即可看到生成的代码。

### 基于注解的View注入：DIActivity
到目前我们还没有使用注解，上面的@Test也没有实际用上，下面我们做一些更加实际的代码生成。实现基于注解的View，代替项目中的`findByView`。这里仅仅是学习怎么用APT，如果真的想用DI框架，推荐使用ButterKnife，功能全面。
1. 第一步，在annotation module创建@DIActivity、@DIView注解。

```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface DIActivity {
    
}
```

```
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DIView {
    int value() default 0;
}
```
1. 创建DIProcessor方法

```
@AutoService(Processor.class)
public class DIProcessor extends AbstractProcessor {

    private Elements elementUtils;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        // 规定需要处理的注解
        return Collections.singleton(DIActivity.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("DIProcessor");
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(DIActivity.class);
        for (Element element : elements) {
            // 判断是否Class
            TypeElement typeElement = (TypeElement) element;
            List<? extends Element> members = elementUtils.getAllMembers(typeElement);
            MethodSpec.Builder bindViewMethodSpecBuilder = MethodSpec.methodBuilder("bindView")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(TypeName.VOID)
                    .addParameter(ClassName.get(typeElement.asType()), "activity");
            for (Element item : members) {
                DIView diView = item.getAnnotation(DIView.class);
                if (diView == null){
                    continue;
                }
                bindViewMethodSpecBuilder.addStatement(String.format("activity.%s = (%s) activity.findViewById(%s)",item.getSimpleName(),ClassName.get(item.asType()).toString(),diView.value()));
            }
            TypeSpec typeSpec = TypeSpec.classBuilder("DI" + element.getSimpleName())
                    .superclass(TypeName.get(typeElement.asType()))
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addMethod(bindViewMethodSpecBuilder.build())
                    .build();
            JavaFile javaFile = JavaFile.builder(getPackageName(typeElement), typeSpec).build();

            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return true;
    }

    private String getPackageName(TypeElement type) {
        return elementUtils.getPackageOf(type).getQualifiedName().toString();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }
}
```
1. 使用DIActivity

```
@DIActivity
public class MainActivity extends Activity {
    @DIView(R.id.text)
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DIMainActivity.bindView(this);
        textView.setText("Hello World!");
    }
}
```
实际上就是通过apt生成以下代码
```
public final class DIMainActivity extends MainActivity {
 public static void bindView(MainActivity activity) {
   activity.textView = (android.widget.TextView) activity.findViewById(R.id.text);
 }
}
```
### 常用方法

##### 常用Element子类
1. TypeElement：类
2. ExecutableElement：成员方法
3. VariableElement：成员变量

##### 通过包名和类名获取TypeName
TypeName targetClassName = ClassName.get("PackageName", "ClassName");

##### 通过Element获取TypeName
TypeName type = TypeName.get(element.asType());

##### 获取TypeElement的包名
String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();

##### 获取TypeElement的所有成员变量和成员方法
List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(typeElement);


### 总结
 推荐阅读dagger2、dbflow、ButterKnife等基于apt的开源项目代码。[JavaPoet](https://github.com/square/javapoet) 也有很多例子可以学习。

#### Example代码
https://github.com/taoweiji/DemoAPT

### 我们的开源项目推荐：
#### Android快速持久化框架：AptPreferences
AptPreferences是基于面向对象设计的快速持久化框架，目的是为了简化SharePreferences的使用，减少代码的编写。可以非常快速地保存基本类型和对象。AptPreferences是基于APT技术实现，在编译期间实现代码的生成，根据不同的用户区分持久化信息。

https://github.com/joyrun/AptPreferences

#### ActivityRouter路由框架：通过注解实现URL打开Activity
基于apt技术，通过注解方式来实现URL打开Activity功能，并支持在WebView和外部浏览器使用，支持多级Activity跳转，支持Bundle、Uri参数注入并转换参数类型。

https://github.com/joyrun/ActivityRouter


