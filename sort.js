var size = 100;
var array = new Array(size);
array.fill(Math.random());
for (var i = 0; i < array.length; i++) {
    array[i] = Math.floor(Math.random() * size);
}

console.log("Array before sorting: ", array);

function bubblesort(arr) {

    let n = arr.length;
    for (let i = 0; i < n; i++) {
        let swapped = false;
        for (let j = 0; j < n - i - 1; j++) {

            if (arr[j] > arr[j + 1]) {
                let tmp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = tmp;
                swapped = true;
            }
        }
        if (!swapped) {
            break;
        }
    }

}

bubblesort(array);
console.log("Array after sorting: ", array);