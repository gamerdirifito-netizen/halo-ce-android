/* UI thread writes one snapshot; the guest reads it on the input thread. */
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <string.h>

static pthread_mutex_t touch_lock = PTHREAD_MUTEX_INITIALIZER;
static int32_t touch_state[7]; /* SDL axes followed by SDL button bits */

JNIEXPORT void JNICALL Java_com_halo_decomp_TouchControls_nativeState(
	JNIEnv *env, jclass cls, jint lx, jint ly, jint rx, jint ry,
	jint lt, jint rt, jint buttons)
{
	int32_t next[] = { lx, ly, rx, ry, lt, rt, buttons };
	(void)env;
	(void)cls;
	pthread_mutex_lock(&touch_lock);
	memcpy(touch_state, next, sizeof(next));
	pthread_mutex_unlock(&touch_lock);
}

void host_touch_read(int32_t *state)
{
	pthread_mutex_lock(&touch_lock);
	memcpy(state, touch_state, sizeof(touch_state));
	pthread_mutex_unlock(&touch_lock);
}
