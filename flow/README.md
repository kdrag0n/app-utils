# flow

Simple lifecycle-aware (State)Flow utilities.

## Example

```kotlin
class ExampleFragment : Fragment() {
    private val model: ExampleViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchStarted {
            model.text.launchCollect(this) {
                binding.textView.text = it
            }

            // Only update (change) events, doesn't get called with initial value
            model.text.launchCollectUpdates(this) {
                logD { "Text updated: $it" }
            }
        }
    }
}


@Composable
fun ExampleScreen(model: ExampleViewModel) {
    val text by model.text.collectAsLifecycleState()

    Text(text)
}
```
